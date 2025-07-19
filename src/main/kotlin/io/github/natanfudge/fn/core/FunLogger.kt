package io.github.natanfudge.fn.core


object FunLogger {
    var level = FunLogLevel.Info
    val hiddenTags = mutableSetOf<String>()
    var performance = true

    fun log(level: FunLogLevel, tag: String = "General", exception: Throwable? = null, message: () -> String) {
        if (level >= this.level && tag !in hiddenTags) {
            println("[$tag]: ${message()}")
            exception?.printStackTrace()
        }
    }

    fun verbose(tag: String = "General", message: () -> String) {
        log(FunLogLevel.Verbose, tag, null, message)
    }

    fun debug(tag: String = "General", message: () -> String) {
        log(FunLogLevel.Debug, tag, null, message)
    }

    fun performance(tag: String = "General", message: () -> String) {
        if (performance && tag !in hiddenTags) {
            println("[$tag]: ${message()}")
        }
    }

    fun info(tag: String = "General", message: () -> String) {
        log(FunLogLevel.Info, tag, null, message)
    }

    fun warn(tag: String = "General", exception: Throwable? = null, message: () -> String) {
        log(FunLogLevel.Warn, tag, exception, message)
    }

    fun error(tag: String = "General", exception: Throwable? = null, message: () -> String) {
        if (FunLogLevel.Error >= this.level && tag !in hiddenTags) {
            System.err.println("[$tag]: ${message()}")
            exception?.printStackTrace()
        }
    }
}


enum class FunLogLevel {
    /**
     * Very detailed logs, useful for tracing execution flow
     */
    Verbose,

    /**
     * Detailed information useful for debugging
     */
    Debug,

    /**
     * General information about normal operation
     */
    Info,

    /**
     * Potential issues that don't prevent normal operation
     */
    Warn,

    /**
     * Serious issues that prevent normal operation
     */
    Error
}


