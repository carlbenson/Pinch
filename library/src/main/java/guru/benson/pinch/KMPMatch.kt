package guru.benson.pinch

/**
 * Knuth-Morris-Pratt Algorithm for Pattern Matching http://stackoverflow.com/questions/1507780/searching-for-a-sequence-of-bytes-in-a-binary-file-with-java
 */

/**
 * Finds the first occurrence of the pattern in the text.
 */
internal fun indexOf(data: ByteArray, pattern: ByteArray): Int {
    val failure = computeFailure(pattern)

    var j = 0
    if (data.isEmpty()) {
        return -1
    }

    for (i in data.indices) {
        while (j > 0 && pattern[j] != data[i]) {
            j = failure[j - 1]
        }
        if (pattern[j] == data[i]) {
            j++
        }
        if (j == pattern.size) {
            return i - pattern.size + 1
        }
    }
    return -1
}

/**
 * Computes the failure function using a boot-strapping process, where the pattern is matched
 * against itself.
 */
private fun computeFailure(pattern: ByteArray): IntArray {
    val failure = IntArray(pattern.size)

    var j = 0
    for (i in 1 until pattern.size) {
        while (j > 0 && pattern[j] != pattern[i]) {
            j = failure[j - 1]
        }
        if (pattern[j] == pattern[i]) {
            j++
        }
        failure[i] = j
    }

    return failure
}
