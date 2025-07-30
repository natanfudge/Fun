package io.github.natanfudge.fn.core

interface Parent<T> {
    val children: List<T>
    fun registerChild(child: T)
    fun unregisterChild(child: T)
    fun clearChildren()
}

class ChildList<T> : Parent<T> {
    override val children = mutableListOf<T>()

    override fun registerChild(child: T) {
        children.add(child)
    }

    override fun unregisterChild(child: T) {
        children.remove(child)
    }

    override fun clearChildren() {
        children.clear()
    }
}

interface Resource {
    val closeAttachments: List<AutoCloseable>
    val id: FunId
    fun alsoClose(closeable: AutoCloseable)

    /**
     * `this` will be closed when this Fun is closed.
     * @return `this`.
     */
    fun <T: AutoCloseable> T.closeWithThis(): T = apply { alsoClose(this@closeWithThis) }
}

