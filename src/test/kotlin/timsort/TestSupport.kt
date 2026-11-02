package timsort

import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicNode
import kotlin.test.fail

/** キーと元の位置を持つ要素。比較はキーだけで行い、index で安定性を検証する。 */
class Item(val key: Int, val index: Int) {
    override fun toString() = "$key#$index"
}

val KEY_ORDER: Comparator<Item> = Comparator { x, y -> Integer.compare(x.key, y.key) }

/** 正解: キー → 元の位置 の全順序でソートしたもの（＝安定ソートの結果と一致する）。 */
private val TOTAL_ORDER: Comparator<Item> = KEY_ORDER.thenComparingInt { it.index }

/** 比較回数を数える Comparator。 */
class CountingComparator<T>(private val c: Comparator<in T>) : Comparator<T> {
    var count = 0L
        private set

    override fun compare(a: T, b: T): Int {
        count++
        return c.compare(a, b)
    }
}

private val threadBean = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

/** block の実行中に現在のスレッドが確保したバイト数。 */
inline fun allocatedBytesDuring(block: () -> Unit): Long {
    val before = currentThreadAllocatedBytes()
    block()
    return currentThreadAllocatedBytes() - before
}

fun currentThreadAllocatedBytes(): Long = threadBean.currentThreadAllocatedBytes

@Volatile
private var sink: Any? = null

/** 計測自体のオーバーヘッド（何もしない block で計測されるバイト数）。 */
private val measurementOverhead: Long by lazy { (1..20).minOf { allocatedBytesDuring { } } }

/** 参照 1 個あたりのバイト数（圧縮参照なら約 4）。大きな参照配列を確保して実測する。 */
private val refBytes: Double by lazy {
    val n = 1_000_000
    (1..5).minOf { allocatedBytesDuring { sink = arrayOfNulls<Any>(n) } - measurementOverhead }.toDouble() / n
}

/**
 * input をソートする間に確保したメモリを参照数に換算して返す。
 * 初回は JIT 前で余計な割り当てが出ることがあるので、複数回測って最小値を取る。
 */
fun allocatedRefs(s: Sorter, input: IntArray): Double {
    val items = Array(input.size) { Item(input[it], it) }
    val copies = Array(8) { items.copyOf() } // 計測対象外で先に確保しておく
    val bytes = copies.minOf { a -> allocatedBytesDuring { s.sort(a, KEY_ORDER) } } - measurementOverhead
    return maxOf(0L, bytes) / refBytes
}

/** pattern を n 要素で生成してソートし、比較回数を返す（正しくソートできたかも検証する）。 */
fun countComparisons(s: Sorter, patternName: String, n: Int): Long {
    val c = CountingComparator(KEY_ORDER)
    assertSorts(s, pattern(patternName).generate(n), comparator = c) { "pattern=$patternName" }
    return c.count
}

fun measure(metric: Metric, s: Sorter, patternName: String, n: Int): Double = when (metric) {
    Metric.COMPARISONS -> countComparisons(s, patternName, n).toDouble()
    Metric.ALLOCATION -> allocatedRefs(s, pattern(patternName).generate(n))
}

/** ランダムな n 要素（相異なる）を比較ソートするのに必要な比較回数の下界。 */
fun log2Factorial(n: Int): Double = (2..n).sumOf { kotlin.math.ln(it.toDouble()) } / kotlin.math.ln(2.0)

/** 各実装ごとに DynamicContainer を作る。 */
fun perSorter(body: (Sorter) -> List<DynamicNode>): List<DynamicNode> =
    SORTERS.map { dynamicContainer(it.name, body(it)) }

/**
 * input を Item 配列にして [from, to) をソートし、結果を参照同一性で検証する。
 * これ 1 回で「並び順」「安定性」「要素の欠落・重複」「範囲外の非破壊」「範囲外の要素を比較しないこと」をすべて確認できる。
 */
fun assertSorts(
    sorter: Sorter,
    input: IntArray,
    from: Int = 0,
    to: Int = input.size,
    comparator: Comparator<Item> = KEY_ORDER,
    context: () -> String = { "" },
) {
    val n = input.size
    val original = Array(n) { Item(input[it], it) }
    val actual = original.copyOf()
    // [from, to) の外の要素を comparator に渡していないか（読むだけでも仕様違反）。Item.index は元の位置。
    val guarded = Comparator<Item> { x, y ->
        if (x.index !in from until to || y.index !in from until to) {
            fail("[${sorter.name}] ソート範囲外の要素を比較した: $x vs $y range=[$from, $to) ${context()}")
        }
        comparator.compare(x, y)
    }
    sorter.sort(actual, from, to, guarded)

    val expected = original.copyOf()
    expected.sortWith(TOTAL_ORDER, from, to)

    val i = (0 until n).firstOrNull { actual[it] !== expected[it] } ?: return

    val kind = when {
        !isPermutationOf(actual, original) -> "要素が欠落・重複している"
        i < from || i >= to -> "ソート範囲外の要素が変更された"
        actual[i].key != expected[i].key -> "並び順が正しくない"
        else -> "安定性違反（等しいキーの元の順序が保たれていない）"
    }
    fail(buildString {
        appendLine("[${sorter.name}] $kind: index=$i expected=${expected[i]} actual=${actual[i]}")
        appendLine("  n=$n range=[$from, $to) ${context()}")
        if (n <= 64) {
            appendLine("  input   =${input.contentToString()}")
            appendLine("  expected=${expected.contentToString()}")
            appendLine("  actual  =${actual.contentToString()}")
        }
    })
}

/** actual が original（index == 位置 の Item 配列）の要素をちょうど 1 回ずつ含むか。 */
fun isPermutationOf(actual: Array<Item>, original: Array<Item>): Boolean {
    val seen = BooleanArray(original.size)
    // 実装のバグで一時配列の null が紛れ込む可能性もあるので null もチェックする
    @Suppress("SENSELESS_COMPARISON")
    return actual.size == original.size && actual.all { item ->
        item != null && item === original[item.index] && !seen[item.index].also { seen[item.index] = true }
    }
}
