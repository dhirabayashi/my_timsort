package timsort

/** 仮実装: 標準ライブラリ（JDK の TimSort）を呼ぶだけ。 */
object StdlibSort : Sorter {
    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        java.util.Arrays.sort(a, fromIndex, toIndex, c)
    }
}
