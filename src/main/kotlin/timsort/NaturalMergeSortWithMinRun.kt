package timsort

import timsort.util.IntQueue
import timsort.util.OrderMode

object NaturalMergeSortWithMinRun: Sorter {
    private const val MIN_RUN = 32

    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        checkRange(fromIndex, toIndex, a.size)

        val size = toIndex - fromIndex
        if (size <= 1) {
            return
        }

        if (size <= 32 ) {
            binaryInsertionSort(a, fromIndex, toIndex, c)
            return
        }

        val runs = findRunsAndReverseDescPart(a, fromIndex, toIndex, c)

        val work = a.copyOfRange(fromIndex, toIndex)

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

            var i = 0

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

            System.arraycopy(work, 0, a, runFirst, end - runFirst)
        }
    }

    private fun <T> binaryInsertionSort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>, paramStart: Int = -1) {
        var start = fromIndex + 1
        if (paramStart != -1) {
            start = paramStart
        } else {
            while (start < toIndex && c.compare(a[start - 1], a[start]) <= 0) {
                start++
            }
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

    private fun <T> findRunsAndReverseDescPart(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>): IntQueue {
        val array = IntArray(Math.ceilDivExact(toIndex - fromIndex, MIN_RUN))
        val runs = IntQueue(array)

        var mode = OrderMode.INIT
        var start = fromIndex

        for (i in fromIndex + 1 until toIndex) {
            val compareResult = c.compare(a[i - 1], a[i])

            when (mode) {
                OrderMode.INIT -> {
                    mode = if (compareResult > 0) {
                        OrderMode.DESCENDING
                    } else {
                        OrderMode.ASCENDING
                    }
                }
                OrderMode.ASCENDING -> {
                    if (compareResult > 0) {
                        if (i - start < MIN_RUN) {
                            binaryInsertionSort(a, start, i + 1, c, i)
                        } else {
                            runs.add(start)
                            start = i
                            mode = OrderMode.INIT
                        }
                    }
                }
                OrderMode.DESCENDING -> {
                    if (compareResult <= 0) {
                        reverse(a, start, i - 1)

                        if (i - start < MIN_RUN) {
                            binaryInsertionSort(a, start, i + 1, c, i)
                            mode = OrderMode.ASCENDING
                        } else {
                            runs.add(start)
                            start = i
                            mode = OrderMode.INIT
                        }
                    }
                }
            }
        }

        runs.add(start)

        if (mode == OrderMode.DESCENDING) {
            reverse(a, start, toIndex - 1)
        }

        return runs
    }

    private fun <T> swap(a: Array<T>, i: Int, j: Int) {
        val tmp = a[i]
        a[i] = a[j]
        a[j] = tmp
    }

    private fun <T> reverse(a: Array<T>, from: Int, to: Int) {
        var i = from
        var j = to

        while (i < j) {
            swap(a, i, j)
            i++
            j--
        }
    }
}

