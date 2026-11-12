package timsort

import timsort.util.OrderMode
import timsort.util.Run
import kotlin.math.min

object ImmatureTimsort: Sorter {
    private const val MIN_RUN = 32

    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        checkRange(fromIndex, toIndex, a.size)

        val size = toIndex - fromIndex
        if (size <= 1) {
            return
        }

        if (size <= 32) {
            binaryInsertionSort(a, fromIndex, toIndex, c)
            return
        }

        val runs = findRunsAndReverseDescPart(a, fromIndex, toIndex, c)

        val work = a.copyOfRange(fromIndex, toIndex)

        while (runs.size != 1) {
            val fst = runs.removeFirst()
            val snd = runs.removeFirst()

            if (fst.start > snd.start) {
                runs.addLast(fst)
                runs.addFirst(snd)
                continue
            }

            merge(a, c, fst.start, snd.start, snd.endExclusive, work)

            runs.add(fst.merged(snd))
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

    private fun <T> findRunsAndReverseDescPart(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>): ArrayDeque<Run> {
        val runs = ArrayDeque<Run>()

        var mode = OrderMode.INIT
        var start = fromIndex

        var i = fromIndex + 1
        while (i < toIndex) {
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
                            binaryInsertionSort(a, start, min(toIndex, start + MIN_RUN), c, i)
                            i = start + MIN_RUN
                            continue
                        } else {
                            runs.add(Run(start, i))
                            start = i
                            mode = OrderMode.INIT
                        }
                    }
                }
                OrderMode.DESCENDING -> {
                    if (compareResult <= 0) {
                        reverse(a, start, i - 1)

                        if (i - start < MIN_RUN) {
                            binaryInsertionSort(a, start, min(toIndex, start + MIN_RUN), c, i)
                            mode = OrderMode.ASCENDING
                            i = start + MIN_RUN
                            continue
                        } else {
                            runs.add(Run(start, i))
                            start = i
                            mode = OrderMode.INIT
                        }
                    }
                }
            }

            i++
        }

        runs.add(Run(start, toIndex))

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

    private fun <T> merge(a: Array<T>, c: Comparator<in T>, leftStart: Int, rightStart: Int, end: Int, work: Array<T>) {
        var rightIndex = rightStart
        var leftIndex = leftStart

        var i = 0

        while (leftIndex != rightStart && rightIndex != end) {
            if (c.compare(a[leftIndex], a[rightIndex]) <= 0) {
                work[i] = a[leftIndex]
                leftIndex++
            } else {
                work[i] = a[rightIndex]
                rightIndex++
            }
            i++
        }

        while (leftIndex != rightStart) {
            work[i] = a[leftIndex]
            leftIndex++
            i++
        }

        while (rightIndex != end) {
            work[i] = a[rightIndex]
            rightIndex++
            i++
        }

        System.arraycopy(work, 0, a, leftStart, end - leftStart)
    }
}
