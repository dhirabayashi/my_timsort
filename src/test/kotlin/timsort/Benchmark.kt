package timsort

import java.io.File

/**
 * 実行時間の簡易ベンチマーク（`./gradlew benchmark`）。
 * JIT のウォームアップ後に各実装を複数回実行し、中央値を JDK と比較する。
 * 実行時間はノイズを含むので、改良の確認には効率レポート（比較回数・割り当て量）と併せて見ること。
 *
 * 2 種類の comparator で測る:
 * - 軽い比較: Integer.compare。実行時間の大半がデータの移動・メモリ確保になる（移動量の改善が見える）
 * - 重い比較: 比較 1 回ごとに計算を挟む。実行時間がほぼ比較回数に比例する（比較回数の改善が見える）
 *
 * オプション: -Pn=要素数（既定 1,000,000。重い比較はその 1/10） -Ppatterns=random,ascending（既定は全パターン）
 */
fun main(args: Array<String>) {
    val n = args.getOrNull(0)?.takeIf { it.isNotBlank() }?.toInt() ?: 1_000_000
    val patterns = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.split(",")?.map { pattern(it.trim()) } ?: PATTERNS

    val report = StringBuilder()
    report.append(benchmark("軽い比較", Comparator { x, y -> Integer.compare(x, y) }, n, patterns))
    report.append(benchmark("重い比較", heavyComparator, maxOf(1, n / 10), patterns))

    File("build/reports").mkdirs()
    File("build/reports/benchmark.md").writeText(report.toString())
}

/**
 * Integer.compare と同じ順序だが、比較のたびに値を撹拌する計算を挟む。
 * mix は全単射なので mix(x) == mix(y) ⇔ x == y となり結果は変わらないが、JIT はそれを証明できず計算を省けない。
 */
private val heavyComparator = Comparator<Int> { x, y -> if (mix(x) == mix(y)) 0 else Integer.compare(x, y) }

private fun mix(v: Int): Int {
    var h = v
    repeat(16) {
        h *= -0x61c88647
        h = h xor (h ushr 16)
    }
    return h
}

private fun benchmark(title: String, comparator: Comparator<Int>, n: Int, patterns: List<Pattern>): String {
    val warmup = 3
    val iterations = 7
    val columns = listOf<Sorter>(JdkReference) + SORTERS

    val report = StringBuilder()
    fun emit(line: String) {
        println(line)
        report.appendLine(line)
    }

    emit("### $title n = %,d（中央値 ms、括弧内は JDK 比）".format(n))
    emit("")
    emit("| pattern | " + columns.joinToString(" | ") { it.name } + " |")
    emit("|---|" + "---:|".repeat(columns.size))

    val totals = DoubleArray(columns.size)
    for (p in patterns) {
        val data = p.generate(n).toTypedArray()
        val medians = columns.map { s ->
            val times = (1..warmup + iterations).map {
                val a = data.copyOf()
                val start = System.nanoTime()
                s.sort(a, comparator)
                val elapsed = System.nanoTime() - start
                check((1 until n).all { i -> a[i - 1] <= a[i] }) { "${s.name} が ${p.name} を正しくソートできていない" }
                elapsed
            }.drop(warmup).sorted()
            times[times.size / 2] / 1e6
        }
        medians.forEachIndexed { i, m -> totals[i] += m }
        emit("| ${p.name} | " + formatRow(medians) + " |")
    }
    emit("| **合計** | " + formatRow(totals.toList()) + " |")
    emit("")
    return report.toString()
}

private fun formatRow(ms: List<Double>): String = ms.mapIndexed { i, m ->
    if (i == 0) "%.1f".format(m) else "%.1f (×%.2f)".format(m, m / ms[0])
}.joinToString(" | ")
