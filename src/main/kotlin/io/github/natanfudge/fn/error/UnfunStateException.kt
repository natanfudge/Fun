package io.github.natanfudge.fn.error

class UnfunStateException(message: String, e: Throwable? = null) : IllegalStateException(message, e)
class UnallowedFunException(message: String): IllegalArgumentException(message)