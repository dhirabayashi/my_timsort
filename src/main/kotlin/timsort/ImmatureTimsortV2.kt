package timsort

import timsort.util.OrderMode
import timsort.util.RunStack
import kotlin.math.min

object ImmatureTimsortV2: Sorter {
    override fun <T> sort(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>) {
        checkRange(fromIndex, toIndex, a.size)

        val size = toIndex - fromIndex
        if (size <= 1) {
            return
        }

        val minRun = computeMinRun(size)

        if (size <= minRun) {
            binaryInsertionSort(a, fromIndex, toIndex, c)
            return
        }

        val work = a.copyOfRange(fromIndex, toIndex)

        val runStack = RunStack(40)

        findRunsAndMerge(a, fromIndex, toIndex, c, work, minRun, runStack)

        while (runStack.size != 1) {
            val i = runStack.lastIndex
            val fstStart = runStack.start(i - 1)
            val sndStart = runStack.start(i)
            val sndEndExclusive = runStack.endExclusive(i)

            merge(a, c, fstStart, sndStart, sndEndExclusive, work)
            runStack.mergeRight()
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

    private fun <T> findRunsAndMerge(
        a: Array<T>,
        fromIndex: Int,
        toIndex: Int,
        c: Comparator<in T>,
        work: Array<T>,
        minRun: Int,
        runStack: RunStack
    ) {
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
                        if (i - start < minRun) {
                            binaryInsertionSort(a, start, min(toIndex, start + minRun), c, i)
                            i = start + minRun
                            continue
                        } else {
                            runStack.add(start, i)
                            mergeIfNeeded(a, c, work, runStack)
                            start = i
                            mode = OrderMode.INIT
                        }
                    }
                }
                OrderMode.DESCENDING -> {
                    if (compareResult <= 0) {
                        reverse(a, start, i - 1)

                        if (i - start < minRun) {
                            binaryInsertionSort(a, start, min(toIndex, start + minRun), c, i)
                            mode = OrderMode.ASCENDING
                            i = start + minRun
                            continue
                        } else {
                            runStack.add(start, i)
                            mergeIfNeeded(a, c, work, runStack)
                            start = i
                            mode = OrderMode.INIT
                        }
                    }
                }
            }

            i++
        }

        if (mode == OrderMode.DESCENDING) {
            reverse(a, start, toIndex - 1)
        }

        runStack.add(start, toIndex)
        mergeIfNeeded(a, c, work, runStack)
    }

    private fun<T> mergeIfNeeded(a: Array<T>, c: Comparator<in T>, work: Array<T>, runStack: RunStack) {
        while (true) {
            if (runStack.size < 2) {
                return
            }

            if (runStack.size == 2) {
                val i = runStack.lastIndex
                val yLength = runStack.length(i - 1)
                val zLength = runStack.length(i)

                if (yLength <= zLength) {
                    val yStart = runStack.start(i - 1)
                    val zStart = runStack.start(i)
                    val zEndExclusive = runStack.endExclusive(i)

                    merge(a, c, yStart, zStart, zEndExclusive, work)
                    runStack.mergeRight()
                }

                return
            }

            val i = runStack.lastIndex
            val xLength = runStack.length(i - 2)
            val yLength = runStack.length(i - 1)
            val zLength = runStack.length(i)

            if (xLength > yLength + zLength && yLength > zLength
                && (runStack.size < 4 || runStack.length(i - 3) > xLength + yLength)) {
                return
            }

            val yStart = runStack.start(i - 1)

            if (xLength < zLength) {
                val xStart = runStack.start(i - 2)
                val yEndExclusive = runStack.endExclusive(i - 1)

                merge(a, c, xStart, yStart, yEndExclusive, work)
                runStack.mergeLeft()
            } else {
                val zStart = runStack.start(i)
                val zEndExclusive = runStack.endExclusive(i)

                merge(a, c, yStart, zStart, zEndExclusive, work)
                runStack.mergeRight()
            }
        }
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

    private fun computeMinRun(size: Int): Int {
        var n = size
        var r = 0

        while (n >= 32) {
            r = r or (n and 1)
            n = n shr 1
        }
        return n + r
    }
}
