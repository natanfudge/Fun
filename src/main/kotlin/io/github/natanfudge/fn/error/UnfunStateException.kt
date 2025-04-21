package io.github.natanfudge.fn.error

class UnfunStateException(message: String) : IllegalStateException(message)
class UnallowedFunException(message: String): IllegalArgumentException(message)