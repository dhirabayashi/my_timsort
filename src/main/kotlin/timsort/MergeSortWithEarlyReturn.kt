package timsort

object MergeSortWithEarlyReturn: Sorter {
    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        checkRange(fromIndex, toIndex, a.size)
        sort(a, fromIndex, toIndex, c, a.clone())
    }

    private fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>, work: Array<T>) {
        val size = toIndex - fromIndex
        if (size <= 1) {
            return
        }

        val half = (fromIndex + toIndex) / 2

        sort(a, fromIndex, half, c, work)
        sort(a, half, toIndex, c, work)

        if (c.compare(a[half - 1], a[half]) <= 0) {
            return
        }

        var i = fromIndex
        var leftIndex = fromIndex
        var rightIndex = half

        while (leftIndex != half && rightIndex != toIndex) {
            if (c.compare(a[leftIndex], a[rightIndex]) <= 0) {
                work[i] = a[leftIndex]
                leftIndex++
            } else {
                work[i] = a[rightIndex]
                rightIndex++
            }
            i++
        }

        while (leftIndex != half) {
            work[i] = a[leftIndex]
            leftIndex++
            i++
        }

        while (rightIndex != toIndex) {
            work[i] = a[rightIndex]
            rightIndex++
            i++
        }

        System.arraycopy(work, fromIndex, a, fromIndex, size)
    }
}
