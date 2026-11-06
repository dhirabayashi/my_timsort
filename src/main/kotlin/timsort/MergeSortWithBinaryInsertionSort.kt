package timsort

object MergeSortWithBinaryInsertionSort: Sorter {
    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        checkRange(fromIndex, toIndex, a.size)
        sort(a, fromIndex, toIndex, c, a.clone())
    }

    private fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>, work: Array<T>) {
        val size = toIndex - fromIndex
        if (size <= 1) {
            return
        }

        if (size <= 32 ) {
            binaryInsertionSort(a, fromIndex, toIndex, c)
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

    private fun <T> binaryInsertionSort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        var start = fromIndex + 1
        while (start < toIndex && c.compare(a[start - 1], a[start]) <= 0) {
            start++
        }

        for (i in start until toIndex) {
            val currentValue = a[i]

            var ng = fromIndex - 1
            var ok = i

            while (ok - ng > 1) {
                val mid = (ng + ok) / 2

                if (c.compare(a[mid], currentValue) > 0) {
                    ok = mid
                } else {
                    ng = mid
                }
            }

            if (ok != i) {
                System.arraycopy(a, ok, a, ok + 1, i - ok)
                a[ok] = currentValue
            }
        }
    }
}
