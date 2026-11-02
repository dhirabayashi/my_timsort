package timsort

import kotlin.random.Random

/** 入力データの生成パターン。seed を固定すれば常に同じ配列を返す。 */
class Pattern(val name: String, private val gen: (n: Int, rnd: Random) -> IntArray) {
    fun generate(n: Int, seed: Long = 1): IntArray =
        gen(n, Random(seed)).also { check(it.size == n) { "pattern $name generated ${it.size} != $n" } }

    override fun toString() = name
}

/**
 * ランの列を作る。各ランはランダム値を昇順（descending(k) なら降順）に並べたもの。
 * 降順ランは重複を含みうる（＝狭義単調減少ではない）ので、ラン反転時の安定性の罠も突ける。
 */
private fun runs(
    n: Int,
    rnd: Random,
    nextLen: (k: Int) -> Int,
    descending: (k: Int) -> Boolean = { false },
    valueRange: Int = maxOf(4, n / 2),
): IntArray {
    val a = IntArray(n)
    var pos = 0
    var k = 0
    while (pos < n) {
        val len = nextLen(k).coerceIn(1, n - pos)
        val run = IntArray(len) { rnd.nextInt(valueRange) }.apply { sort() }
        if (descending(k)) run.reverse()
        run.copyInto(a, pos)
        pos += len
        k++
    }
    return a
}

private fun fibonacciUpTo(n: Int): List<Int> {
    val fibs = mutableListOf(1, 2)
    while (fibs.last() + fibs[fibs.size - 2] <= maxOf(n, 2)) fibs += fibs.last() + fibs[fibs.size - 2]
    return fibs
}

/** TimSort の minRun 計算（JDK と同じ）。 */
private fun minRunLength(n: Int): Int {
    var m = n
    var r = 0
    while (m >= 32) {
        r = r or (m and 1)
        m = m shr 1
    }
    return m + r
}

/**
 * ランスタックが最も深くなるラン長の列を作る。
 * de Gouw et al., "OpenJDK's java.utils.Collection.sort() is broken" (CAV 2015) の生成法。
 *
 * mergeCollapse が「スタック上位 2 段」しか不変条件をチェックしない（JDK 7/8 初期版の）実装だと、
 * n = 119,150 でスタック 21 段（JDK 初期値 19 を超える）、n = 2^26 で 41 段（同 40 を超える）が必要になる。
 * 不変条件を 3 段目までチェックする正しい実装なら 22 段以内に収まる。
 */
fun runStackWorstCase(n: Int): IntArray {
    if (n < 2) return IntArray(n)
    val m = minRunLength(n).toLong()
    val runs = ArrayDeque<Long>()

    fun addWrongElem(x0: Long) {
        var x = x0
        while (x >= 2 * m + 1) {
            val newTotal = when (x) {
                in 3 * m + 3..4 * m + 1 -> 2 * m + 1
                in 5 * m + 5..6 * m + 5 -> 3 * m + 3
                in 8 * m + 9..10 * m + 9 -> 5 * m + 5
                in 13 * m + 15..16 * m + 17 -> 8 * m + 9
                else -> x / 2 + 1
            }
            runs.addFirst(x - newTotal)
            x = newTotal
        }
        runs.addFirst(x)
    }

    var total = 0L
    var y = m + 4
    var x = m
    while (total + y + x <= n) {
        total += x + y
        addWrongElem(x)
        runs.addFirst(y)
        x = y + runs[1] + 1
        y += x + 1
    }
    if (total + x <= n) {
        total += x
        addWrongElem(x)
    }
    runs.addLast(n - total)

    // 各ランを 0,0,...,0,1 にする（次のランは 0 で始まるのでそこで途切れる）
    val a = IntArray(n)
    var end = -1L
    for (len in runs) {
        end += len
        a[end.toInt()] = 1
    }
    a[n - 1] = 0
    return a
}

/**
 * 不変条件が正しい（runLen[i-2] > runLen[i-1] + runLen[i] かつ runLen[i-1] > runLen[i]）実装でも
 * ランスタックが最も深くなる入力。ラン長を大きい順に「上 2 段の和 + 1」で並べるので、push しても一切マージされない。
 * runStackWorstCase は「不変条件の誤り」を突く入力で、こちらは「固定長スタックの長さ不足」を突く入力。
 * 例: n = 119,150 では 16 段積まれる（JDK のスタック長は 24 段）。
 */
fun runStackDeepestValid(n: Int): IntArray {
    if (n < 2) return IntArray(n)
    val m = minRunLength(n)
    val top = mutableListOf(m, m + 1) // スタックの上（後に積まれる）から順に
    while (top.sum() + top[top.size - 1] + top[top.size - 2] + 1 <= n) top += top[top.size - 1] + top[top.size - 2] + 1
    while (top.size > 1 && top.sum() > n) top.removeAt(top.size - 1)
    val lens = top.reversed().toMutableList()
    lens[0] += n - lens.sum() // 余りは一番下（最初）のランに足す

    // 各ランを 0,0,...,0,1 にする（runStackWorstCase と同じ）
    val a = IntArray(n)
    var end = -1
    for (len in lens) {
        end += len
        a[end] = 1
    }
    a[n - 1] = 0
    return a
}

val PATTERNS: List<Pattern> = listOf(
    // ランダム系
    Pattern("random") { n, r -> IntArray(n) { r.nextInt() } },
    Pattern("random_0..n") { n, r -> IntArray(n) { r.nextInt(maxOf(1, n)) } },
    Pattern("few_unique_4") { n, r -> IntArray(n) { r.nextInt(4) } },
    Pattern("sqrt_unique") { n, r -> val k = maxOf(1, kotlin.math.sqrt(n.toDouble()).toInt()); IntArray(n) { r.nextInt(k) } },
    Pattern("all_equal") { n, _ -> IntArray(n) { 42 } },
    Pattern("extremes") { n, r ->
        val v = intArrayOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, 0, 1, Int.MAX_VALUE - 1, Int.MAX_VALUE)
        IntArray(n) { v[r.nextInt(v.size)] }
    },

    // ソート済み系
    Pattern("ascending") { n, _ -> IntArray(n) { it } },
    Pattern("descending") { n, _ -> IntArray(n) { n - it } },
    Pattern("ascending_dups") { n, _ -> IntArray(n) { it / 3 } },
    Pattern("descending_dups") { n, _ -> IntArray(n) { (n - it) / 3 } },
    Pattern("nearly_sorted") { n, r ->
        val a = IntArray(n) { it }
        repeat(maxOf(1, n / 100)) {
            if (n >= 2) {
                val i = r.nextInt(n); val j = r.nextInt(n)
                val t = a[i]; a[i] = a[j]; a[j] = t
            }
        }
        a
    },
    Pattern("sorted+random_tail") { n, r -> val k = maxOf(1, n / 64); IntArray(n) { if (it < n - k) it else r.nextInt(maxOf(1, n)) } },
    Pattern("random_head+sorted") { n, r -> val k = maxOf(1, n / 64); IntArray(n) { if (it < k) r.nextInt(maxOf(1, n)) else it } },
    Pattern("push_front") { n, _ -> IntArray(n) { if (it == n - 1) 0 else it + 1 } },
    Pattern("push_middle") { n, _ -> IntArray(n) { if (it == n / 2) n else it } },
    Pattern("rotated") { n, _ -> IntArray(n) { (it + n / 2) % maxOf(1, n) } },

    // ラン構造系
    Pattern("sawtooth_2") { n, _ -> IntArray(n) { it % 2 } },
    Pattern("sawtooth_7") { n, _ -> IntArray(n) { it % 7 } },
    Pattern("sawtooth_31") { n, _ -> IntArray(n) { it % 31 } },
    Pattern("sawtooth_32") { n, _ -> IntArray(n) { it % 32 } },
    Pattern("sawtooth_33") { n, _ -> IntArray(n) { it % 33 } },
    Pattern("sawtooth_1000") { n, _ -> IntArray(n) { it % 1000 } },
    Pattern("desc_sawtooth_32") { n, _ -> IntArray(n) { 32 - it % 32 } },
    Pattern("organ_pipe") { n, _ -> IntArray(n) { if (it < n / 2) it else n - it } },
    Pattern("zigzag") { n, _ -> IntArray(n) { if (it % 2 == 0) it / 2 else n - 1 - it / 2 } },
    Pattern("interleaved_halves") { n, _ ->
        // 前半が偶数・後半が奇数の昇順。マージ時に左右が 1 要素ずつ交互に勝つ（ギャロップの最悪ケース）
        val h = (n + 1) / 2
        IntArray(n) { if (it < h) 2 * it else 2 * (it - h) + 1 }
    },
    Pattern("block_interleaved_halves") { n, _ ->
        // 前半・後半とも昇順で、128 要素のブロック単位で交互に勝つ（ギャロップが効くケース）
        val h = (n + 1) / 2
        val b = 128
        IntArray(n) { if (it < h) 2 * b * (it / b) + it % b else 2 * b * ((it - h) / b) + b + (it - h) % b }
    },
    Pattern("random_runs") { n, r -> runs(n, r, { 1 + r.nextInt(64) }) },
    Pattern("random_long_runs") { n, r -> runs(n, r, { 1 + r.nextInt(maxOf(1, n / 8)) }) },
    Pattern("alternating_runs") { n, r -> runs(n, r, { 1 + r.nextInt(100) }, { it % 2 == 1 }) },
    // 値が 4 種類しかないラン同士のマージ。境界に等しいキーのかたまりが来るので、
    // ギャロップの左右（gallopLeft / gallopRight）の取り違えによる安定性違反を突ける
    Pattern("runs_few_unique") { n, r -> runs(n, r, { 1 + r.nextInt(maxOf(1, n / 8)) }, valueRange = 4) },
    Pattern("runs_few_unique_short") { n, r -> runs(n, r, { 1 + r.nextInt(64) }, valueRange = 4) },
    Pattern("fibonacci_runs") { n, r -> val f = fibonacciUpTo(n).reversed(); runs(n, r, { f[it % f.size] }) },
    Pattern("fibonacci_runs_asc") { n, r -> val f = fibonacciUpTo(n); runs(n, r, { f[it % f.size] }) },
    Pattern("runstack_worst") { n, _ -> runStackWorstCase(n) },
)

fun pattern(name: String): Pattern = PATTERNS.first { it.name == name }
