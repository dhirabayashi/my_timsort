package timsort

import timsort.util.OrderMode
import timsort.util.RunStack
import kotlin.math.max
import kotlin.math.min

object TimsortWithAdaptiveMinGallop: Sorter {
    private const val MIN_GALLOP = 7

    private class MergeState<T>(val work: Array<T>) {
        var minGallop = MIN_GALLOP
    }

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

        val state = MergeState(a.copyOfRange(fromIndex, fromIndex + size / 2))

        val runStack = RunStack(40)

        findRunsAndMerge(a, fromIndex, toIndex, c, state, minRun, runStack)

        while (runStack.size != 1) {
            val i = runStack.lastIndex
            val fstStart = runStack.start(i - 1)
            val sndStart = runStack.start(i)
            val sndEndExclusive = runStack.endExclusive(i)

            merge(a, c, fstStart, sndStart, sndEndExclusive, state)
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
        state: MergeState<T>,
        minRun: Int,
        runStack: RunStack
    ) {
        var runStart = fromIndex
        while (runStart < toIndex) {
            val runEndExclusive = findRunAndConvertToAscending(a, runStart, toIndex, c, minRun)

            runStack.add(runStart, runEndExclusive)
            mergeIfNeeded(a, c, state, runStack)

            runStart = runEndExclusive
        }
    }

    private fun <T> findRunAndConvertToAscending(a: Array<T>, start: Int, toIndex: Int, c: Comparator<in T>, minRun: Int): Int {
        var mode = OrderMode.INIT

        var i = start + 1
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
                            return i
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
                            return i
                        }
                    }
                }
            }

            i++
        }

        if (mode == OrderMode.DESCENDING) {
            reverse(a, start, toIndex - 1)
        }
        return toIndex
    }

    private fun<T> mergeIfNeeded(a: Array<T>, c: Comparator<in T>, state: MergeState<T>, runStack: RunStack) {
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

                    merge(a, c, yStart, zStart, zEndExclusive, state)
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

                merge(a, c, xStart, yStart, yEndExclusive, state)
                runStack.mergeLeft()
            } else {
                val zStart = runStack.start(i)
                val zEndExclusive = runStack.endExclusive(i)

                merge(a, c, yStart, zStart, zEndExclusive, state)
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

    private fun <T> merge(a: Array<T>, c: Comparator<in T>, leftStart: Int, rightStart: Int, end: Int, state: MergeState<T>) {
        val trimmedLeftStart = upperBound(a, leftStart, rightStart, a[rightStart], c, searchStart = leftStart)
        if (trimmedLeftStart == rightStart) {
            return
        }

        val trimmedEnd = lowerBound(a, rightStart, end, a[rightStart - 1], c, searchStart = end - 1)

        if (rightStart - trimmedLeftStart <= trimmedEnd - rightStart) {
            mergeLo(a, c, trimmedLeftStart, rightStart, trimmedEnd, state)
        } else {
            mergeHi(a, c, trimmedLeftStart, rightStart, trimmedEnd, state)
        }
    }

    private fun <T> upperBound(a: Array<T>, from: Int, to: Int, key: T, c: Comparator<in T>, searchStart: Int): Int {
        var ng: Int
        var ok: Int

        if (c.compare(a[searchStart], key) <= 0) {
            ng = searchStart
            ok = searchStart + 1
            while (ok < to && c.compare(a[ok], key) <= 0) {
                ng = ok
                val step = ok - searchStart + 1
                ok = if (to - ok <= step) to else ok + step
            }
        } else {
            ok = searchStart
            ng = searchStart - 1
            while (ng >= from && c.compare(a[ng], key) > 0) {
                ok = ng
                val step = searchStart - ng + 1
                ng = if (ng - from < step) from - 1 else ng - step
            }
        }

        while (ok - ng > 1) {
            val mid = (ng + ok) / 2

            if (c.compare(a[mid], key) > 0) {
                ok = mid
            } else {
                ng = mid
            }
        }
        return ok
    }

    private fun <T> lowerBound(a: Array<T>, from: Int, to: Int, key: T, c: Comparator<in T>, searchStart: Int): Int {
        var ng: Int
        var ok: Int

        if (c.compare(a[searchStart], key) < 0) {
            ng = searchStart
            ok = searchStart + 1
            while (ok < to && c.compare(a[ok], key) < 0) {
                ng = ok
                val step = ok - searchStart + 1
                ok = if (to - ok <= step) to else ok + step
            }
        } else {
            ok = searchStart
            ng = searchStart - 1
            while (ng >= from && c.compare(a[ng], key) >= 0) {
                ok = ng
                val step = searchStart - ng + 1
                ng = if (ng - from < step) from - 1 else ng - step
            }
        }

        while (ok - ng > 1) {
            val mid = (ng + ok) / 2

            if (c.compare(a[mid], key) >= 0) {
                ok = mid
            } else {
                ng = mid
            }
        }
        return ok
    }

    private fun <T> mergeLo(a: Array<T>, c: Comparator<in T>, leftStart: Int, rightStart: Int, end: Int, state: MergeState<T>) {
        val work = state.work
        val leftLength = rightStart - leftStart
        System.arraycopy(a, leftStart, work, 0, leftLength)

        var leftIndex = 0
        var rightIndex = rightStart
        var dest = leftStart

        var minGallop = state.minGallop
        var galloping = false
        var leftWins = 0
        var rightWins = 0

        while (leftIndex != leftLength && rightIndex != end) {
            if (!galloping) {
                if (c.compare(work[leftIndex], a[rightIndex]) <= 0) {
                    a[dest] = work[leftIndex]
                    leftIndex++
                    leftWins++
                    rightWins = 0
                } else {
                    a[dest] = a[rightIndex]
                    rightIndex++
                    rightWins++
                    leftWins = 0
                }
                dest++

                if (leftWins >= minGallop || rightWins >= minGallop) {
                    galloping = true
                }
                continue
            }

            val leftEnd = upperBound(work, leftIndex, leftLength, a[rightIndex], c, searchStart = leftIndex)
            val leftCount = leftEnd - leftIndex
            System.arraycopy(work, leftIndex, a, dest, leftCount)
            leftIndex = leftEnd
            dest += leftCount
            if (leftIndex == leftLength) {
                break
            }

            val rightEnd = lowerBound(a, rightIndex, end, work[leftIndex], c, searchStart = rightIndex)
            val rightCount = rightEnd - rightIndex
            System.arraycopy(a, rightIndex, a, dest, rightCount)
            rightIndex = rightEnd
            dest += rightCount
            if (rightIndex == end) {
                break
            }

            minGallop--
            if (leftCount < MIN_GALLOP && rightCount < MIN_GALLOP) {
                galloping = false
                leftWins = 0
                rightWins = 0
                minGallop = max(minGallop, 0) + 2
            }
        }

        System.arraycopy(work, leftIndex, a, dest, leftLength - leftIndex)
        state.minGallop = max(minGallop, 1)
    }

    private fun <T> mergeHi(a: Array<T>, c: Comparator<in T>, leftStart: Int, rightStart: Int, end: Int, state: MergeState<T>) {
        val work = state.work
        val rightLength = end - rightStart
        System.arraycopy(a, rightStart, work, 0, rightLength)

        var leftIndex = rightStart - 1
        var rightIndex = rightLength - 1
        var dest = end - 1

        var minGallop = state.minGallop
        var galloping = false
        var leftWins = 0
        var rightWins = 0

        while (leftIndex >= leftStart && rightIndex >= 0) {
            if (!galloping) {
                if (c.compare(a[leftIndex], work[rightIndex]) > 0) {
                    a[dest] = a[leftIndex]
                    leftIndex--
                    leftWins++
                    rightWins = 0
                } else {
                    a[dest] = work[rightIndex]
                    rightIndex--
                    rightWins++
                    leftWins = 0
                }
                dest--

                if (leftWins >= minGallop || rightWins >= minGallop) {
                    galloping = true
                }
                continue
            }

            val leftFrom = upperBound(a, leftStart, leftIndex + 1, work[rightIndex], c, searchStart = leftIndex)
            val leftCount = leftIndex + 1 - leftFrom
            System.arraycopy(a, leftFrom, a, dest - leftCount + 1, leftCount)
            leftIndex = leftFrom - 1
            dest -= leftCount
            if (leftIndex < leftStart) {
                break
            }

            val rightFrom = lowerBound(work, 0, rightIndex + 1, a[leftIndex], c, searchStart = rightIndex)
            val rightCount = rightIndex + 1 - rightFrom
            System.arraycopy(work, rightFrom, a, dest - rightCount + 1, rightCount)
            rightIndex = rightFrom - 1
            dest -= rightCount
            if (rightIndex < 0) {
                break
            }

            minGallop--
            if (leftCount < MIN_GALLOP && rightCount < MIN_GALLOP) {
                galloping = false
                leftWins = 0
                rightWins = 0
                minGallop = max(minGallop, 0) + 2
            }
        }

        System.arraycopy(work, 0, a, leftStart, rightIndex + 1)
        state.minGallop = max(minGallop, 1)
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
