# my_timsort

普通のマージソートから始めて、段階的に改良しながら TimSort を実装する学習用プロジェクト（Kotlin / JVM 25）。

各段階を別クラスとして実装し、すべての段階に同じテストをかけることで、

- **デグレしていないこと**（ソート順・安定性・例外仕様など）
- **性能が向上していること**（比較回数・メモリ割り当て量・実行時間）

を段階ごとに確認できるようにしている。

## 使い方

```sh
./gradlew test        # 正しさのテスト + 効率の目標 + 効率レポート
./gradlew benchmark   # 実行時間のベンチマーク（JDK と比較）
./gradlew benchmark -Pn=200000 -Ppatterns=random,ascending   # 要素数・パターンを絞る
```

| 出力 | 内容 |
|---|---|
| `build/reports/comparison-counts.md` | 比較回数の表（n = 100, 10,000） |
| `build/reports/allocations.md` | メモリ割り当て量の表（n = 10,000） |
| `build/reports/benchmark.md` | 実行時間の表（軽い比較 / 重い比較） |

比較回数と割り当て量のレポートは `./gradlew test` のたびに標準出力にも表示される。

## 段階の追加のしかた

1. `src/main/kotlin/timsort/` に [`Sorter`](src/main/kotlin/timsort/Sorter.kt) を実装したクラスを新しく作る（前の段階のクラスは残す）。
2. [`Registry.kt`](src/test/kotlin/timsort/Registry.kt) の `STEPS` の末尾に、その段階で新たに達成した目標と一緒に追加する。

```kotlin
val STEPS: List<Step> = listOf(
    Step(MergeSort),
    Step(NaturalMergeSort, Goal.ASCENDING_LINEAR, Goal.DESCENDING_LINEAR),
    Step(MergeSortWithBinaryInsertion, Goal.NEAR_OPTIMAL_RANDOM),
    Step(MergeSortReusingTmp, Goal.TMP_REUSE),
    Step(TimSort, Goal.GALLOPING, Goal.TMP_HALF),
    Step(TunedTimSort, Goal.JDK_PARITY),
)
```

- 登録した全段階が同じテストで検証され、レポートでは横に並んで「1 つ前の段階からの増減率」が表示される。
- **目標は累積する。** ある段階で宣言した目標は以降の全段階でも検証されるので、前の段階で達成した性能を落とすとテストが失敗する。
- `StdlibSort`（標準ライブラリを呼ぶだけの仮実装）は最初の段階を追加したら外す。JDK の値は常に `JDK(参考)` 列に出る。

## Sorter の仕様

```kotlin
fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>)
```

- 安定ソートであること（比較結果が 0 の要素同士は元の順序を保つ）
- `[fromIndex, toIndex)` だけを並べ替え、範囲外の要素は変更しないし比較もしない
- 範囲が不正なら `IndexOutOfBoundsException` か `IllegalArgumentException` を投げ、配列は変更しない
- comparator が投げた例外はそのまま伝播させる
- 要素数が 1 以下なら comparator を呼ばない
- 全順序の契約を破る comparator でも、無限ループや配列外アクセスを起こさない（正常終了か `IllegalArgumentException`）

## テストの構成

`src/test/kotlin/timsort/`

| ファイル | 内容 |
|---|---|
| `CorrectnessTest.kt` | 正しさのテスト |
| `EfficiencyTest.kt` | 効率の目標の検証と、比較回数・割り当て量のレポート |
| `Benchmark.kt` | 実行時間のベンチマーク（`./gradlew benchmark` の本体） |
| `Goals.kt` | 効率の目標の定義 |
| `Patterns.kt` | 入力データのパターン（35 種） |
| `Registry.kt` | テスト対象の段階の一覧 |
| `TestSupport.kt` | 検証・計測の共通処理 |

### 正しさの検証方法

入力を「キー + 元の位置」を持つ `Item` の配列にしてソートし、正解（キー → 元の位置 の全順序でソートしたもの）と**参照同一性で**突き合わせる。1 回の比較で、並び順・安定性・要素の欠落や重複・範囲外の書き換えをまとめて検出し、失敗時はどれに当たるかと再現用の情報（パターン・n・seed、小さい配列なら入力そのもの）を表示する。

### 正しさのテスト一覧（`CorrectnessTest`）

| テスト | 狙い |
|---|---|
| 境界ケース | 空・1 要素・2 要素・Int の極値など |
| パターン別_全サイズ | 全パターン × サイズ 0〜140 の全部と、2 冪前後などの大きめのサイズ |
| 全順列_n0から8 | 長さ 8 までの全順列 |
| 全列挙_重複あり | 値 2〜4 種類の全列挙（安定性） |
| ファジング_マージが起きるサイズ | n = 32〜4999 をランダムなパターン・seed で 6000 回（ギャロップのバグ探し） |
| 部分範囲のソート | ランダムな範囲と代表的な範囲 |
| 不正な範囲は例外を投げ配列を変更しない | 範囲チェック |
| 要素数1以下ならcomparatorを呼ばない | 無駄な比較をしない |
| いろいろな要素型とComparator | String、逆順、null を含む配列、大文字小文字無視での安定性 |
| ランスタック最悪ケース / 最深ケース / n2の26乗 | ランスタックのあふれ（下記） |
| 大きな入力_n100万 | 100 万要素の代表的なパターン |
| 契約違反のcomparatorでも壊れない | ランダム・常に -1・非推移的な comparator |
| comparatorの例外はそのまま伝播する | 例外の伝播 |

#### ランスタックのテストについて

JDK 7/8 初期版の TimSort には、マージ条件（ランスタックの不変条件）を上位 2 段しかチェックしないためにスタックがあふれるバグがあった（de Gouw et al., *OpenJDK's java.utils.Collection.sort() is broken*, CAV 2015）。`runStackWorstCase(n)` は論文の方法でこれを起こす入力を作る。

- n = 119,150 で 21 段必要（JDK の初期値 19 段を超える）
- n = 2^26 で 41 段必要（同 40 段を超える）

固定長のランスタックを使い、不変条件のチェックが不十分だと `ArrayIndexOutOfBoundsException` で失敗する。n = 2^26 のテストのため `test` のヒープ上限は 4GB にしてある（マージソートの段階では数十秒かかる）。

## 効率の測り方

| 指標 | 測り方 | 見えるもの |
|---|---|---|
| 比較回数 | comparator を包んで呼び出し回数を数える | ラン検出・二分挿入・ギャロップの効果 |
| メモリ割り当て量 | `ThreadMXBean` で sort 中にスレッドが確保したバイト数を測り、「参照 n 個の配列 1 本 = 1.00」に換算 | 一時配列の使い回し・サイズの削減 |
| 実行時間 | ウォームアップ後の中央値を JDK と比較 | データ移動量を含む総合的な速さ |

比較回数と割り当て量は seed 固定なら毎回同じ値になるので、小さな差でも信頼できる。実行時間はノイズを含むので判定には使わず、ベンチマークでの確認用。

ベンチマークは 2 種類の comparator で測る。

- **軽い比較**（`Integer.compare`）: 時間の大半がデータの移動・メモリ確保になる
- **重い比較**（比較ごとに計算を挟む、要素数は 1/10）: 時間がほぼ比較回数に比例する

### 効率の目標（`Goals.kt`、n = 10,000）

| 目標 | 指標 | 内容 |
|---|---|---|
| `ASCENDING_LINEAR` | 比較回数 | 昇順・全要素等しい入力は n−1 回（ラン検出） |
| `DESCENDING_LINEAR` | 比較回数 | 狭義降順の入力は n−1 回（降順ランの反転） |
| `NEAR_OPTIMAL_RANDOM` | 比較回数 | ランダム入力は log2(n!) の 10% 増し以内（二分挿入ソート） |
| `GALLOPING` | 比較回数 | ブロック単位で片側が勝ち続けるマージは 1.4n 回以内（ギャロップ） |
| `TMP_REUSE` | 割り当て量 | 一時配列をマージのたびに作り直さない（総量 2n 以下） |
| `TMP_HALF` | 割り当て量 | 2 本のランのマージで一時配列は約 n/2 だけ（mergeLo / mergeHi） |
| `JDK_PARITY` | 比較回数 | 全パターンで JDK の 3% 増し以内（仕上げ） |

JDK の実装は常に全目標についてテストされるので、目標値そのものの妥当性も確認される。

`EfficiencyTest` の比較回数レポートは、上記の目標とは別に「比較回数が 3·n·log2(n) + n を超えない（二乗オーダーになっていない）」ことも検証する。

## 入力パターン（`Patterns.kt`）

| 系統 | パターン |
|---|---|
| ランダム | `random`, `random_0..n`, `few_unique_4`, `sqrt_unique`, `all_equal`, `extremes` |
| ソート済みに近い | `ascending`, `descending`, `ascending_dups`, `descending_dups`, `nearly_sorted`, `sorted+random_tail`, `random_head+sorted`, `push_front`, `push_middle`, `rotated` |
| ラン構造 | `sawtooth_2/7/31/32/33/1000`, `desc_sawtooth_32`, `organ_pipe`, `zigzag`, `interleaved_halves`, `block_interleaved_halves`, `random_runs`, `random_long_runs`, `alternating_runs`, `runs_few_unique`, `runs_few_unique_short`, `fibonacci_runs`, `fibonacci_runs_asc`, `runstack_worst` |

`descending_dups` は重複を含む降順で、降順ランの反転を「狭義」でなく行ったときの安定性違反を突く。`runs_few_unique*` は境界に等しいキーのかたまりが来るランのマージで、ギャロップの左右の取り違えを突く。
