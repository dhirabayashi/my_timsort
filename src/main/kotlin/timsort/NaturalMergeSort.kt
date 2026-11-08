package timsort

object NaturalMergeSort: Sorter {
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

        val runs = findRuns(a, fromIndex, toIndex, c)

        while (runs.size != 1) {
            val runFirst = runs.removeFirst()
            val mid = runs.removeFirst()

            runs.add(runFirst)

            if (runFirst > mid) {
                runs.addFirst(mid)
                continue
            }

            var rightIndex = mid
            var leftIndex = runFirst

            val end = if (runs.size == 1 || runs.first() < rightIndex) {
                toIndex
            } else {
                runs.first()
            }

            var i = leftIndex

            while (leftIndex != mid && rightIndex != end) {
                if (c.compare(a[leftIndex], a[rightIndex]) <= 0) {
                    work[i] = a[leftIndex]
                    leftIndex++
                } else {
                    work[i] = a[rightIndex]
                    rightIndex++
                }
                i++
            }

            while (leftIndex != mid) {
                work[i] = a[leftIndex]
                leftIndex++
                i++
            }

            while (rightIndex != end) {
                work[i] = a[rightIndex]
                rightIndex++
                i++
            }

            System.arraycopy(work, runFirst, a, runFirst, end - runFirst)
        }
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

    private fun <T> findRuns(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>): ArrayDeque<Int> {
        val runs = ArrayDeque<Int>()
        runs.add(fromIndex)

        for (i in fromIndex + 1 until toIndex) {
            if (c.compare(a[i - 1], a[i]) > 0) {
                runs.add(i)
            }
        }

        return runs
    }
}
