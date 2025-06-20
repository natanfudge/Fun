package io.github.natanfudge.fn.util

/**
 * Hands out consecutive indices starting at 0.
 * - Calling [get] returns the lowest available index.
 * - Calling [free] releases an index so it can be reused.
 *
 *  ✔️  Always returns the smallest freed index first.
 *  ✔️  Never returns the same index twice unless it was freed.
 *  ✔️  Shrinks the “tail” (highest indices) when you free them,
 *      so the sequence stays as small as possible.
 */
class ConsecutiveIndexProvider {

    /** Min-heap of released indices (lowest comes out first). */
    private val freed = java.util.PriorityQueue<Int>()

    /** HashSet so we never enqueue the same index twice. */
    private val freedSet = HashSet<Int>()

    /** The next brand-new index we’ll hand out if none are freed. */
    private var next = 0

    /**
     * The total amount of space required to store all active indices in an array
     */
    val totalSize get() = next

    /** Get the next available index. */
    @Synchronized
    fun get(): Int =
        if (freed.isNotEmpty()) {
            val idx = freed.poll()
            freedSet.remove(idx)
            idx
        } else {
            next++      // use a fresh index
            next - 1
        }

    /**
     * Release a previously obtained index.
     *
     *  *Indices outside the current range or double-frees throw an error.*
     */
    @Synchronized
    fun free(index: Int) {

        require(index in 0 until next) { "Index $index was never allocated." }
        if (!freedSet.add(index)) error("Index $index already freed.")

        freed.add(index)

        // If we just freed the current highest index, compact the tail
        if (index == next - 1) compactTail()
    }

    /** Pull back [next] while the top of the range is in the free set. */
    private fun compactTail() {
        while (next > 0 && freedSet.remove(next - 1)) {
            freed.remove(next - 1)
            next--
        }
    }
}