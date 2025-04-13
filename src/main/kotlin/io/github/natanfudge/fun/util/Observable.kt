package natan.io.github.natanfudge.`fun`.util

class Observable<T> {
    private val listeners = mutableListOf<() -> Unit>()
    fun observe(onEvent: () -> Unit) {
        listeners.add(onEvent)
    }

    fun emit() {
        listeners.forEach { it() }
    }
}