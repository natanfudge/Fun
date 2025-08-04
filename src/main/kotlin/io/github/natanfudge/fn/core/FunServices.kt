package io.github.natanfudge.fn.core

/**
 * Services are singleton [Fun]s that may be optionally initialized
 */
class FunServices {
    private val services = mutableMapOf<ServiceKey<*>, Any?>()
    fun <T> registerUnscoped(service: T, key: ServiceKey<T>) {
        services[key] = service
    }

    fun <T> unregister(key: ServiceKey<T>) {
        services.remove(key)
    }

    fun <T> get(key: ServiceKey<T>): T {
        val service = services[key] ?: throw MissingFunException("Cannot use ${key.name} service, as it was not created.")
        return service as T
    }
}

class MissingFunException(message: String) : Exception(message)

fun <T : Fun> T.exposeAsService(key: ServiceKey<T>) {
    context.services.registerUnscoped(this, key)
    alsoClose { context.services.unregister(key) }
}


inline fun <reified T> serviceKey(name: String = T::class.simpleName ?: "") = ServiceKey<T>(name)

class ServiceKey<T>(val name: String) {
    context(fn: Fun)
    val current: T get() = fn.context.services.get(this)
}