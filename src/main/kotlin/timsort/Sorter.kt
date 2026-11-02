package timsort

/**
 * ソートアルゴリズムの共通インターフェース。
 *
 * 段階的な改良版（MergeSort → ... → TimSort）はそれぞれこのインターフェースを実装した
 * 別クラスとして作り、テスト側の `SORTERS`（src/test/kotlin/timsort/Registry.kt）に登録する。
 * そうすると全段階が同じテストで検証され、比較回数・実行時間も横並びで比較できる。
 *
 * 要求仕様:
 * - 安定ソートであること（比較結果が 0 の要素同士は元の順序を保つ）
 * - [fromIndex, toIndex) の範囲だけを並べ替え、範囲外の要素には触れないこと
 * - 範囲が不正なら IndexOutOfBoundsException か IllegalArgumentException を投げること
 *   （配列は変更しない）
 * - comparator が投げた例外はそのまま呼び出し元に伝播させること
 */
interface Sorter {
    val name: String get() = javaClass.simpleName

    fun <T> sort(a: Array<T>, c: Comparator<in T>) = sort(a, 0, a.size, c)

    fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>)

    fun checkRange(fromIndex: Int, toIndex: Int, size: Int) {
        if (fromIndex < 0) {
            throw ArrayIndexOutOfBoundsException()
        }
        if (toIndex > size) {
            throw ArrayIndexOutOfBoundsException()
        }
        if (fromIndex > toIndex) {
            throw IllegalArgumentException()
        }
    }
}
