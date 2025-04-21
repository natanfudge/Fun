package io.github.natanfudge.fn.util

import io.github.natanfudge.fn.error.UnallowedFunException

class ImmutableCollection<E>(private val collection: Collection<E>) : MutableCollection<E> {
    override val size: Int
        get() = collection.size

    override fun contains(element: E): Boolean = collection.contains(element)

    override fun containsAll(elements: Collection<E>): Boolean = collection.containsAll(elements)

    override fun isEmpty(): Boolean = collection.isEmpty()

    override fun iterator(): MutableIterator<E> = ImmutableIterator(collection.iterator())

    override fun add(element: E): Boolean {
        dontAllowModification()
    }

    override fun addAll(elements: Collection<E>): Boolean {
        dontAllowModification()
    }

    override fun clear() {
        dontAllowModification()
    }

    override fun remove(element: E): Boolean {
        dontAllowModification()
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        dontAllowModification()
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        dontAllowModification()
    }

    private class ImmutableIterator<E>(private val iterator: Iterator<E>) : MutableIterator<E> {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): E = iterator.next()

        override fun remove() {
            dontAllowModification()
        }
    }
}

private fun dontAllowModification(): Nothing {
    throw UnallowedFunException("mutating the .values and .keys collections from a FunMap is not allowed")
}

class ImmutableSet<E>(private val set: Set<E>) : MutableSet<E> {
    override val size: Int
        get() = set.size

    override fun contains(element: E): Boolean = set.contains(element)

    override fun containsAll(elements: Collection<E>): Boolean = set.containsAll(elements)

    override fun isEmpty(): Boolean = set.isEmpty()

    override fun iterator(): MutableIterator<E> = ImmutableIterator(set.iterator())

    override fun add(element: E): Boolean {
        dontAllowModification()
    }

    override fun addAll(elements: Collection<E>): Boolean {
        dontAllowModification()
    }

    override fun clear() {
        dontAllowModification()
    }

    override fun remove(element: E): Boolean {
        dontAllowModification()
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        dontAllowModification()
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        dontAllowModification()
    }

    private class ImmutableIterator<E>(private val iterator: Iterator<E>) : MutableIterator<E> {
        override fun hasNext(): Boolean = iterator.hasNext()

        override fun next(): E = iterator.next()

        override fun remove() {
            dontAllowModification()
        }
    }
}