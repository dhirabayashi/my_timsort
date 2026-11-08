package timsort

object NaturalMergeSort: Sorter {
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

        val runs = findRuns(a, fromIndex, toIndex, c)

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

    private fun <T> findRuns(a: Array<T>, fromIndex: Int, toIndex: Int, c: Comparator<in T>): IntQueue {
        val array = IntArray(toIndex - fromIndex)
        val runs = IntQueue(array)
        runs.add(fromIndex)

        for (i in fromIndex + 1 until toIndex) {
            if (c.compare(a[i - 1], a[i]) > 0) {
                runs.add(i)
            }
        }

        return runs
    }
}

private class IntQueue(
    val array: IntArray,
) {
    var size = 0
    private var head = -1
    private var tail = -1

    fun first(): Int {
        if (size == 0) {
            throw NoSuchElementException()
        }

        return array[head]
    }

    fun removeFirst(): Int {
        if (size == 0) {
            throw NoSuchElementException()
        }

        val first = array[head]

        if (head == array.size - 1) {
            head = 0
        } else {
            head++
        }
        size--

        return first
    }

    fun add(elem: Int) {
        if (array.size == size) {
            error("容量が足りない")
        }

        if (tail == array.size - 1) {
            tail = 0
        } else {
            tail++
        }
        array[tail] = elem
        size++

        if (head == -1) {
            head = 0
        }
    }

    fun addFirst(elem: Int) {
        if (array.size == size) {
            error("容量が足りない")
        }

        if (head == 0) {
            head = array.size - 1
        } else {
            head--
        }
        array[head] = elem
        size++

        if (tail == -1) {
            tail = 0
        }
    }
}
