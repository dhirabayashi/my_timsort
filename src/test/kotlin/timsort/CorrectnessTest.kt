package timsort

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.time.Duration
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 正しさ（ソート順・安定性・非破壊性・例外仕様）のテスト。
 * SORTERS に登録された全実装に対して同じテストを実行する。
 */
class CorrectnessTest {

    companion object {
        /**
         * 0..140 は全サイズ（TimSort の minRun は 32..64、小配列の閾値も 32/64 あたりなので
         * その前後をすべて含める）。それ以上は 2 冪の前後と半端なサイズ。
         */
        val SIZES: List<Int> = (0..140).toList() +
            listOf(255, 256, 257, 511, 512, 513, 1000, 1023, 1024, 1025, 2047, 2048, 2049, 4097, 10_000, 65_537)

        private fun seedsFor(n: Int): List<Long> = if (n <= 140) listOf(1L, 2L, 3L) else listOf(1L)
    }

    @TestFactory
    fun `境界ケース`() = perSorter { s ->
        listOf(
            "空配列" to intArrayOf(),
            "1要素" to intArrayOf(5),
            "2要素_昇順" to intArrayOf(1, 2),
            "2要素_降順" to intArrayOf(2, 1),
            "2要素_等しい" to intArrayOf(7, 7),
            "3要素_降順" to intArrayOf(3, 2, 1),
            "3要素_降順_重複" to intArrayOf(2, 2, 1),
            "Int極値" to intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE, 0, -1, Int.MAX_VALUE, Int.MIN_VALUE, 1),
        ).map { (name, input) -> dynamicTest(name) { assertSorts(s, input) } }
    }

    @TestFactory
    fun `パターン別_全サイズ`() = perSorter { s ->
        PATTERNS.map { p ->
            dynamicTest(p.name) {
                for (n in SIZES) for (seed in seedsFor(n)) {
                    assertSorts(s, p.generate(n, seed)) { "pattern=${p.name} seed=$seed" }
                }
            }
        }
    }

    @TestFactory
    fun `全順列_n0から8`() = perSorter { s ->
        (0..8).map { n ->
            dynamicTest("n=$n") {
                val a = IntArray(n) { it }
                do {
                    assertSorts(s, a.copyOf()) { "permutation" }
                } while (nextPermutation(a))
            }
        }
    }

    /** 重複を含む全列挙。安定性の検証に効く。 */
    @TestFactory
    fun `全列挙_重複あり`() = perSorter { s ->
        listOf(2 to 14, 3 to 9, 4 to 7).map { (alphabet, maxLen) ->
            dynamicTest("値${alphabet}種類_長さ${maxLen}まで") {
                for (len in 0..maxLen) {
                    val a = IntArray(len)
                    do {
                        assertSorts(s, a.copyOf()) { "alphabet=$alphabet" }
                    } while (increment(a, alphabet))
                }
            }
        }
    }

    @TestFactory
    fun `部分範囲のソート`() = perSorter { s ->
        listOf(
            dynamicTest("ランダムな範囲") {
                val r = Random(42)
                repeat(2000) { trial ->
                    val n = r.nextInt(0, 300)
                    val from = r.nextInt(0, n + 1)
                    val to = r.nextInt(from, n + 1)
                    assertSorts(s, pattern("random_0..n").generate(n, trial.toLong()), from, to) { "trial=$trial" }
                }
            },
            dynamicTest("全パターン_n1000_代表的な範囲") {
                val n = 1000
                val ranges = listOf(0 to n, 1 to n - 1, n / 3 to 2 * n / 3, 0 to n / 2, n / 2 to n, 500 to 501, 500 to 500)
                for (p in PATTERNS) for ((from, to) in ranges) {
                    assertSorts(s, p.generate(n), from, to) { "pattern=${p.name}" }
                }
            },
        )
    }

    @TestFactory
    fun `不正な範囲は例外を投げ配列を変更しない`() = perSorter { s ->
        listOf(-1 to 5, 0 to 11, 6 to 5, 11 to 11, -2 to -1, 10 to 0).map { (from, to) ->
            dynamicTest("[$from, $to)") {
                val a = Array(10) { Item(9 - it, it) }
                val before = a.copyOf()
                val e = runCatching { s.sort(a, from, to, KEY_ORDER) }.exceptionOrNull()
                assertTrue(
                    e is IndexOutOfBoundsException || e is IllegalArgumentException,
                    "IndexOutOfBoundsException か IllegalArgumentException が期待されたが $e",
                )
                assertTrue(a.indices.all { a[it] === before[it] }, "配列が変更された")
            }
        }
    }

    @TestFactory
    fun `要素数1以下ならcomparatorを呼ばない`() = perSorter { s ->
        listOf(Triple(0, 0, 0), Triple(1, 0, 1), Triple(10, 3, 3), Triple(10, 3, 4), Triple(10, 10, 10)).map { (n, from, to) ->
            dynamicTest("n=$n [$from, $to)") {
                val c = CountingComparator(KEY_ORDER)
                s.sort(Array(n) { Item(n - it, it) }, from, to, c)
                assertEquals(0, c.count)
            }
        }
    }

    @TestFactory
    fun `いろいろな要素型とComparator`() = perSorter { s ->
        listOf(
            dynamicTest("String_自然順序") {
                val r = Random(1)
                val a = Array(500) { "s" + r.nextInt(1000) }
                val expected = a.sortedArray()
                s.sort(a, naturalOrder())
                assertContentEquals(expected, a)
            },
            dynamicTest("Int_逆順") {
                val r = Random(2)
                val a = Array(500) { r.nextInt() }
                val expected = a.sortedArrayDescending()
                s.sort(a, reverseOrder())
                assertContentEquals(expected, a)
            },
            dynamicTest("null を含む配列_nullsFirst") {
                // 実装が null を番兵や「空き」の印として使っていないかの確認
                val r = Random(3)
                val a = Array(500) { if (r.nextInt(5) == 0) null else r.nextInt(100) }
                val expected = a.sortedWith(nullsFirst(naturalOrder()))
                s.sort(a, nullsFirst(naturalOrder()))
                assertContentEquals(expected, a.toList())
            },
            dynamicTest("大文字小文字無視_安定性") {
                val r = Random(4)
                val letters = listOf("a", "A", "b", "B", "c", "C")
                for (n in listOf(10, 100, 1000)) {
                    val a = Array(n) { letters[r.nextInt(letters.size)] + (it % 3) }
                    val expected = a.sortedWith(String.CASE_INSENSITIVE_ORDER) // List のソートは安定
                    s.sort(a, String.CASE_INSENSITIVE_ORDER)
                    assertContentEquals(expected, a.toList(), "n=$n")
                }
            },
        )
    }

    /**
     * マージ・ギャロップが起きるサイズ（32 以上）を多数の seed で叩くファジング。
     * ギャロップのバグは「特定の連勝数」と「境界の重複キー」が重なったときにしか出ないので、量で探す。
     * 失敗時のメッセージに trial と seed が出るので、それで再現できる。
     */
    @TestFactory
    fun `ファジング_マージが起きるサイズ`() = perSorter { s ->
        listOf(32 until 300, 300 until 5000).map { range ->
            dynamicTest("n=${range.first}..${range.last}") {
                val r = Random(range.first.toLong())
                repeat(3000) { trial ->
                    val p = PATTERNS[r.nextInt(PATTERNS.size)]
                    val n = r.nextInt(range.first, range.last + 1)
                    val seed = r.nextLong()
                    assertSorts(s, p.generate(n, seed)) { "trial=$trial pattern=${p.name} seed=$seed" }
                }
            }
        }
    }

    /**
     * ランスタックの不変条件を上位 2 段しかチェックしない（JDK 7/8 初期版の）mergeCollapse で
     * スタックがあふれる入力。n = 119,150 で 21 段が必要になる（JDK 初期値は 19 段）。
     * 正しい不変条件なら n = 2^31 でも 40 段強に収まる。
     */
    @TestFactory
    fun `ランスタック最悪ケース`() = perSorter { s ->
        listOf(1541, 119_150, 1_000_000).map { n ->
            dynamicTest("n=$n") {
                assertTimeoutPreemptively(Duration.ofSeconds(60)) {
                    assertSorts(s, runStackWorstCase(n)) { "pattern=runstack_worst" }
                }
            }
        }
    }

    /**
     * 不変条件が正しくても固定長のランスタックが足りなくなる入力（スタック長の見積もり誤りを検出する）。
     * n は JDK のスタック長の区切り（120 / 1542 / 119151）の前後と、その間の値。
     */
    @TestFactory
    fun `ランスタック最深ケース_正しい不変条件`() = perSorter { s ->
        listOf(64, 119, 120, 1541, 1542, 50_000, 119_150, 119_151, 1_000_000).map { n ->
            dynamicTest("n=$n") {
                assertTimeoutPreemptively(Duration.ofSeconds(60)) {
                    assertSorts(s, runStackDeepestValid(n)) { "pattern=runstack_deepest_valid" }
                }
            }
        }
    }

    /** n = 2^26 で 41 段必要（JDK 初期値 40 段を超える）。メモリ節約のため Item ではなく 0/1 の Integer で検証する。 */
    @TestFactory
    fun `ランスタック最悪ケース_n2の26乗`() = perSorter { s ->
        listOf(dynamicTest("n=2^26") {
            val input = runStackWorstCase(1 shl 26)
            val ones = input.count { it == 1 }
            val a = Array(input.size) { input[it] } // 0 と 1 は Integer キャッシュなので参照配列ぶんのメモリで済む
            s.sort(a, naturalOrder())
            val zeros = a.size - ones
            assertTrue((0 until zeros).all { a[it] == 0 } && (zeros until a.size).all { a[it] == 1 }, "正しくソートされていない")
        })
    }

    @TestFactory
    fun `大きな入力_n100万`() = perSorter { s ->
        listOf("random", "ascending", "descending_dups", "sawtooth_2", "random_runs", "alternating_runs", "fibonacci_runs", "interleaved_halves")
            .map { name ->
                dynamicTest(name) {
                    assertTimeoutPreemptively(Duration.ofSeconds(60)) {
                        assertSorts(s, pattern(name).generate(1_000_000)) { "pattern=$name" }
                    }
                }
            }
    }

    /**
     * 全順序の契約を破る comparator でも、無限ループや配列外アクセスを起こさないこと。
     * 正常終了するか IllegalArgumentException（JDK の "Comparison method violates its general contract!"）
     * のどちらかを許容する。正常終了した場合は要素が欠落・重複していないことも確認する。
     */
    @TestFactory
    fun `契約違反のcomparatorでも壊れない`() = perSorter { s ->
        val comparators: List<Pair<String, (Random) -> Comparator<Item>>> = listOf(
            "ランダムな結果" to { r -> Comparator { _, _ -> r.nextInt(3) - 1 } },
            "常に-1" to { _ -> Comparator { _, _ -> -1 } },
            "常に1" to { _ -> Comparator { _, _ -> 1 } },
            "非推移的(じゃんけん)" to { _ -> Comparator { x, y -> ((x.key - y.key) % 3 + 3) % 3 - 1 } },
        )
        comparators.map { (name, make) ->
            dynamicTest(name) {
                for (n in listOf(2, 3, 10, 31, 32, 33, 64, 65, 100, 1000, 10_000, 100_000)) for (seed in 1L..3L) {
                    val r = Random(seed)
                    val original = Array(n) { Item(r.nextInt(n), it) }
                    val a = original.copyOf()
                    val result = assertTimeoutPreemptively(Duration.ofSeconds(10), { "n=$n seed=$seed で終了しない" }) {
                        runCatching { s.sort(a, make(Random(seed))) }
                    }
                    val e = result.exceptionOrNull()
                    when {
                        e == null -> assertTrue(isPermutationOf(a, original), "n=$n seed=$seed: 要素が欠落・重複している")
                        e !is IllegalArgumentException -> fail("n=$n seed=$seed: 想定外の例外 $e", e)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `comparatorの例外はそのまま伝播する`() = perSorter { s ->
        listOf(1, 10, 100, 1000, 5000).map { after ->
            dynamicTest("${after}回目の比較で例外") {
                class Boom : RuntimeException()
                val a = Array(10_000) { Item(Random(it).nextInt(), it) }
                var calls = 0
                assertFailsWith<Boom> {
                    s.sort(a, Comparator { x, y -> if (++calls == after) throw Boom() else KEY_ORDER.compare(x, y) })
                }
            }
        }
    }

    /** 辞書順で次の順列に並べ替える。最後の順列なら false。 */
    private fun nextPermutation(a: IntArray): Boolean {
        var i = a.size - 2
        while (i >= 0 && a[i] >= a[i + 1]) i--
        if (i < 0) return false
        var j = a.size - 1
        while (a[j] <= a[i]) j--
        a[i] = a[j].also { a[j] = a[i] }
        a.reverse(i + 1, a.size)
        return true
    }

    /** a を base 進数の数とみなして 1 増やす。桁あふれしたら false。 */
    private fun increment(a: IntArray, base: Int): Boolean {
        for (i in a.indices.reversed()) {
            if (++a[i] < base) return true
            a[i] = 0
        }
        return false
    }
}
