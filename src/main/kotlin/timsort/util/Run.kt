package timsort.util

data class Run(
    val start: Int,
    val endExclusive: Int,
) {
    val length: Int
        get() = endExclusive - start

    fun merged(next: Run): Run {
        return Run(start, next.endExclusive)
    }
}
