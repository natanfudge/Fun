package io.github.natanfudge.fn.core

data class Tag<T>(val name: String)

interface Taggable {
    fun <T> getTag(tag: Tag<T>): T?
    fun <T> setTag(tag: Tag<T>, value: T?)
    fun removeTag(tag: Tag<*>)
    fun hasTag(tag: Tag<*>): Boolean
}

class TagMap: Taggable {
    private val tags = mutableMapOf<String, Any?>()

    override fun <T> getTag(tag: Tag<T>): T? {
        return tags[tag.name] as T?
    }

    override fun removeTag(tag: Tag<*>) {
        tags.remove(tag.name)
    }

    override fun <T> setTag(tag: Tag<T>, value: T?) {
        tags[tag.name] = value
    }

    override fun hasTag(tag: Tag<*>): Boolean {
        return tag.name in tags
    }
}