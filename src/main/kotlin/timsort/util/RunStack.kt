package timsort.util

class RunStack(size: Int) {
    private val starts = IntArray(size)
    private val lengths = IntArray(size)
    private var i = -1

    val size: Int
        get() = i + 1

    val lastIndex: Int
        get() = i

    fun add(start: Int, endExclusive: Int) {
        i++
        starts[i] = start
        lengths[i] = endExclusive - start
    }

    fun start(i: Int): Int {
        return starts[i]
    }

    fun length(i: Int): Int {
        return lengths[i]
    }

    fun endExclusive(i: Int): Int {
        return starts[i] + lengths[i]
    }

    fun mergeLeft() {
        lengths[i - 2] += lengths[i - 1]
        starts[i - 1] = starts[i]
        lengths[i - 1] = lengths[i]
        i--
    }

    fun mergeRight() {
        lengths[i - 1] += lengths[i]
        i--
    }
}
