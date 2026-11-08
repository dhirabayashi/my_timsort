package timsort.util

class IntQueue(
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
