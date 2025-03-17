@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

import java.util.concurrent.atomic.*

/**
 * @author Vasilkov Dmitry
 */
class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val newTail = Node(element, curTail)

            if (curTail.next.compareAndSet(null, newTail)) {
                updateTail(curTail, newTail)
                removeIfExtractedOrRemoved(curTail)
                return
            } else {
                moveTailToNext(curTail)
            }
        }
    }

    private fun updateTail(curTail: Node<E>, newTail: Node<E>) {
        tail.compareAndSet(curTail, newTail)
    }

    private fun removeIfExtractedOrRemoved(curTail: Node<E>) {
        if (curTail.extractedOrRemoved) {
            curTail.remove()
        }
    }

    private fun moveTailToNext(curTail: Node<E>) {
        tail.compareAndSet(curTail, curTail.next.get())
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val nextHead = curHead.next.get()

            if (nextHead == null) {
                return null
            }

            if (head.compareAndSet(curHead, nextHead)) {
                removePrevReferenceFromNextHead(nextHead)
                val element = extractElementFromNextHead(nextHead)
                if (element != null) {
                    return element
                }
            }
        }
    }

    private fun removePrevReferenceFromNextHead(nextHead: Node<E>) {
        nextHead.prev.set(null)
    }

    private fun extractElementFromNextHead(nextHead: Node<E>): E? {
        return if (nextHead.markExtractedOrRemoved()) {
            nextHead.element.also {
                nextHead.element = null
            }
        } else {
            null
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(head.get().prev.get() == null) {
            "`head.prev` must be null"
        }
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.get()
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.get()
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.get()
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = AtomicReference<Node<E>?>(null)
        val prev = AtomicReference(prev)

        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            val removed = markExtractedOrRemoved()
            val curNext = next.get()
            val curPrev = prev.get()

            if (curPrev == null || curNext == null) {
                return removed
            }

            curPrev.next.set(curNext)
            do {
                val prevOfNext = curNext.prev.get()
            } while (prevOfNext != null && !curNext.prev.compareAndSet(prevOfNext, curPrev))

            tryRemove(curPrev)
            tryRemove(curNext)

            return removed
        }

        private fun tryRemove(cur:  Node<E>) {
            if (cur.extractedOrRemoved) {
                cur.remove()
            }
        }
    }
}