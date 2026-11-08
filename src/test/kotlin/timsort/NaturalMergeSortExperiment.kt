package timsort

import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class NaturalMergeSortExperiment {

    @Test
    @Ignore
    fun `マージを1回ずつ観察する`() {
        val keys = intArrayOf(7, 6, 5, 4, 3, 2, 1, 0)

        println("NaturalMergeSort")
        val natural = observe(NaturalMergeSort, keys, skip = keys.size - 1) // 最初の n-1 回はラン検出
        println()
        println("MergeSort")
        val merge = observe(MergeSort, keys, skip = 0)
        println()
        println("NaturalMergeSort: $natural 回")
        println("MergeSort: $merge 回")
        println("差: ${natural - merge} 回")
    }

    private class Merge(val before: List<Int>, val from: Int, val mid: Int) {
        var to = mid + 1
        var compares = 0
    }

    // 配列の中身が変わったら新しいマージが始まったとみなす。
    // 左の範囲は最初の比較の 2 要素の位置から、右の範囲の終わりは比較された右側の要素の位置からわかる。
    private fun observe(sorter: Sorter, keys: IntArray, skip: Int): Int {
        val n = keys.size
        val a = Array(n) { Item(keys[it], it) }
        val merges = mutableListOf<Merge>()
        var calls = 0
        var lastSeen: List<Item>? = null

        val observer = Comparator<Item> { x, y ->
            calls++
            if (calls > skip) {
                val now = a.toList()
                if (now != lastSeen) {
                    lastSeen = now
                    merges += Merge(now.map { it.key }, now.indexOf(x), now.indexOf(y))
                }
                val m = merges.last()
                m.to = maxOf(m.to, now.indexOf(y) + 1)
                m.compares++
            }
            KEY_ORDER.compare(x, y)
        }
        sorter.sort(a, observer)

        for (m in merges) {
            println(listOf(
                m.before.subList(0, m.from).joinToString(" "),
                "[ " + m.before.subList(m.from, m.mid).joinToString(" ") + " ]",
                "[ " + m.before.subList(m.mid, m.to).joinToString(" ") + " ]",
                m.before.subList(m.to, n).joinToString(" "),
            ).filter { it.isNotEmpty() }.joinToString(" "))
            println("  比較 ${m.compares} 回")
        }
        println(a.joinToString(" ") { it.key.toString() })
        return calls
    }
}
