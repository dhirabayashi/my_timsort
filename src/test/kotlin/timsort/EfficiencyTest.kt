package timsort

import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.log2
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 効率（比較回数・メモリ割り当て量）のテストとレポート。
 * どちらも seed 固定なら決定的なので、実行時間と違ってノイズなしに「改良で効率が上がったか」を確認できる。
 * データの移動量など両者に表れない改良は `./gradlew benchmark` の実行時間で確認する。
 */
class EfficiencyTest {

    /**
     * Registry の Step で宣言した目標（累積）を検証する。
     * JdkReference は全目標を満たすはずなので、目標値そのものの妥当性確認も兼ねる。
     */
    @TestFactory
    fun `効率の目標`() = (listOf(JdkReference) + SORTERS).map { s ->
        val goals = if (s === JdkReference) Goal.entries.toSet() else GOALS_BY_SORTER.getValue(s)
        dynamicContainer(s.name, goals.flatMap { goal ->
            goal.patterns.map { p ->
                dynamicTest("$goal: $p") {
                    val limit = goal.limit(GOAL_N, p)
                    val value = measure(goal.metric, s, p, GOAL_N)
                    assertTrue(
                        value <= limit,
                        "[${s.name}] ${goal.description}: pattern=$p n=$GOAL_N ${goal.metric.label} " +
                            "%.0f > 目標 %.0f".format(value, limit),
                    )
                }
            }
        })
    }

    /**
     * 全パターン × 全実装の比較回数を表にして標準出力と build/reports/comparison-counts.md に出す。
     * 各実装の列には「1 つ前の実装」からの増減率を併記する。
     * アサーションは O(n log n) を大きく外れていないか（＝二乗オーダーになっていないか）のみ。
     * ある実装が正しくソートできなかった場合もレポートは最後まで出し、そのセルを ✗ にする。
     */
    @Test
    fun `比較回数レポート`() {
        val violations = mutableListOf<String>()
        val text = buildString {
            for (n in listOf(100, 10_000)) {
                val nLogN = n * log2(n.toDouble())
                val bound = 3 * nLogN + n
                appendLine("### 比較回数 n = %,d".format(n))
                appendLine()
                appendLine("参考値: n-1 = %,d / log2(n!) = %,.0f（ランダム入力での下界） / n·log2(n) = %,.0f".format(n - 1, log2Factorial(n), nLogN))
                appendLine()
                append(table(violations, { "%,.0f".format(it) }) { s, p ->
                    countComparisons(s, p.name, n).toDouble().also {
                        if (n >= 1000 && it > bound) {
                            violations += "[${s.name}] ${p.name} n=$n: 比較回数 %.0f > 上限 %.0f（O(n log n) の想定を超えた）".format(it, bound)
                        }
                    }
                })
            }
        }
        writeReport("comparison-counts.md", text)
        if (violations.isNotEmpty()) fail(violations.joinToString("\n"))
    }

    /**
     * 全パターン × 全実装のメモリ割り当て量を表にして標準出力と build/reports/allocations.md に出す。
     * 値は「sort 中に確保した総量 ÷ (参照 1 個のサイズ × n)」。一時配列を n 要素ぶん 1 回確保すれば 1.00。
     * アサーションなし（目標は `効率の目標` で検証する）。
     */
    @Test
    fun `メモリ割り当てレポート`() {
        val n = 10_000
        val violations = mutableListOf<String>()
        val text = buildString {
            appendLine("### メモリ割り当て量 n = %,d（n 要素の参照配列 1 本ぶんを 1.00 とした総割り当て量）".format(n))
            appendLine()
            append(table(violations, { "%.2f".format(it) }) { s, p -> allocatedRefs(s, p.generate(n)) / n })
        }
        writeReport("allocations.md", text)
        if (violations.isNotEmpty()) fail(violations.joinToString("\n"))
    }

    /** パターン × 実装の表を作る。各実装の列には 1 つ前の実装からの増減率を併記する。 */
    private fun table(violations: MutableList<String>, format: (Double) -> String, measure: (Sorter, Pattern) -> Double): String {
        val columns = listOf<Sorter>(JdkReference) + SORTERS
        val totals = DoubleArray(columns.size)
        return buildString {
            appendLine("| pattern | " + columns.joinToString(" | ") { it.name } + " |")
            appendLine("|---|" + "---:|".repeat(columns.size))
            for (p in PATTERNS) {
                val values = columns.map { s ->
                    runCatching { measure(s, p) }
                        .onFailure { violations += it.message ?: it.toString() }
                        .getOrNull()
                }
                values.forEachIndexed { i, v -> if (v != null) totals[i] += v }
                appendLine("| ${p.name} | " + formatRow(values, format) + " |")
            }
            appendLine("| **合計** | " + formatRow(totals.toList(), format) + " |")
            appendLine()
        }
    }

    /** 先頭（JDK 参考値）はそのまま、2 番目は数値のみ、3 番目以降は前の実装との差分を併記する。 */
    private fun formatRow(values: List<Double?>, format: (Double) -> String): String = values.mapIndexed { i, v ->
        val prev = values.getOrNull(i - 1)
        when {
            v == null -> "✗"
            i < 2 || prev == null -> format(v)
            else -> "${format(v)} (${diff(prev, v)})"
        }
    }.joinToString(" | ")

    private fun diff(prev: Double, cur: Double): String = when {
        format2(prev) == format2(cur) -> "±0"
        prev == 0.0 -> "+∞"
        else -> "%+.1f%%".format((cur - prev) * 100.0 / prev)
    }

    private fun format2(v: Double) = "%.2f".format(v)

    private fun writeReport(fileName: String, text: String) {
        println(text)
        File("build/reports").mkdirs()
        File("build/reports/$fileName").writeText(text)
    }
}
