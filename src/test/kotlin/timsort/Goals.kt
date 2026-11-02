package timsort

/** 効率の指標。どちらも seed 固定なら（ほぼ）決定的なので、段階間の比較に使える。 */
enum class Metric(val label: String) {
    /** comparator の呼び出し回数。 */
    COMPARISONS("比較回数"),

    /** sort 呼び出し中に確保したメモリの総量を「参照何個ぶんか」に換算したもの（n 要素の配列 1 本 ≒ n）。 */
    ALLOCATION("割り当て量（参照数換算）"),
}

/**
 * 効率の目標。n = [GOAL_N] で、指定パターンの指標（[metric]）が limit 以下であることを検証する。
 * TimSort（JdkReference）はすべて満たす。各段階が達成したものを Registry の Step に宣言する。
 */
enum class Goal(
    val description: String,
    val metric: Metric,
    val patterns: List<String>,
    val limit: (n: Int, pattern: String) -> Double,
) {
    ASCENDING_LINEAR(
        "昇順・全要素等しい入力は n-1 回（ラン検出）",
        Metric.COMPARISONS,
        listOf("ascending", "ascending_dups", "all_equal"),
        { n, _ -> n - 1.0 },
    ),
    DESCENDING_LINEAR(
        "狭義降順の入力は n-1 回（降順ランの反転）",
        Metric.COMPARISONS,
        listOf("descending"),
        { n, _ -> n - 1.0 },
    ),
    NEAR_OPTIMAL_RANDOM(
        "ランダム入力は log2(n!) の 10% 増し以内（短いランを線形でなく二分挿入ソートで伸ばす）",
        Metric.COMPARISONS,
        listOf("random", "random_0..n"),
        { n, _ -> 1.10 * log2Factorial(n) },
    ),
    GALLOPING(
        "ブロック単位で片側が勝ち続けるマージは 1.4n 回以内（ギャロップ）",
        Metric.COMPARISONS,
        listOf("rotated", "block_interleaved_halves"),
        { n, _ -> 1.4 * n },
    ),
    TMP_REUSE(
        "一時配列をマージのたびに作り直さない（総割り当てが 2n 要素ぶん以下）",
        Metric.ALLOCATION,
        listOf("random", "random_runs", "sawtooth_32"),
        { n, _ -> 2.0 * n },
    ),
    TMP_HALF(
        "2 本のランのマージで一時配列は小さい方のランぶん（約 n/2）だけ確保する（mergeLo / mergeHi）",
        Metric.ALLOCATION,
        listOf("interleaved_halves", "rotated"),
        { n, _ -> 0.6 * n },
    ),

    /**
     * 上の目標は「仕組みがあるか」しか見ないので、minGallop の適応やマージ順序の細部を
     * 間違えても（結果は正しいまま）比較回数が 5〜70% 悪化するのを見逃す。それを全パターンで捕まえる仕上げの目標。
     * JDK と違う設計（別のマージ方針など）を意図的に選んで少し悪化するなら、許容幅を調整すること。
     */
    JDK_PARITY(
        "全パターンで JDK の比較回数の ${"%.0f".format((JDK_PARITY_TOLERANCE - 1) * 100)}% 増し以内（ギャロップの適応・マージ順序の細部）",
        Metric.COMPARISONS,
        PATTERNS.map { it.name },
        { n, p -> JDK_PARITY_TOLERANCE * jdkComparisons(p, n) },
    ),
}

const val GOAL_N = 10_000

/** [Goal.JDK_PARITY] の許容倍率。 */
const val JDK_PARITY_TOLERANCE = 1.03

/** JDK が pattern を n 要素でソートしたときの比較回数（seed は既定値で固定なので決定的）。 */
fun jdkComparisons(patternName: String, n: Int): Long {
    val input = pattern(patternName).generate(n)
    val c = CountingComparator(KEY_ORDER)
    JdkReference.sort(Array(n) { Item(input[it], it) }, c)
    return c.count
}
