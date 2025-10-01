package io.github.natanfudge.fn.core

import androidx.compose.runtime.mutableStateSetOf
import io.github.natanfudge.fn.compose.funedit.StringSetEditor

interface ILogger {
    fun log(level: FunLogLevel, tag: String = "General", exception: Throwable? = null, message: () -> String)
    fun verbose(tag: String = "General", message: () -> String)
    fun debug(tag: String = "General", message: () -> String)
    fun performance(tag: String = "General", message: () -> String)
    fun info(tag: String = "General", message: () -> String)
    fun warn(tag: String = "General", exception: Throwable? = null, message: () -> String)
    fun error(tag: String = "General", exception: Throwable? = null, message: () -> String)
}

object SimpleLogger : ILogger {
    override fun log(level: FunLogLevel, tag: String, exception: Throwable?, message: () -> String) {
        println("$level: [$tag]: ${message()}")
        exception?.printStackTrace()
    }

    override fun verbose(tag: String, message: () -> String) {
        log(FunLogLevel.Verbose, tag, null, message)
    }

    override fun debug(tag: String, message: () -> String) {
        log(FunLogLevel.Verbose, tag, null, message)
    }

    override fun performance(tag: String, message: () -> String) {
        log(FunLogLevel.Info, "performance-$tag", null, message)
    }

    override fun info(tag: String, message: () -> String) {
        log(FunLogLevel.Info, tag, null, message)
    }

    override fun warn(tag: String, exception: Throwable?, message: () -> String) {
        log(FunLogLevel.Warn, tag, exception, message)
    }

    override fun error(tag: String, exception: Throwable?, message: () -> String) {
        log(FunLogLevel.Error, tag, exception, message)
    }

}


class FunLogger : Fun("FunLogger"), ILogger {
    companion object {
        val service = serviceKey<FunLogger>()
    }

    init {
        exposeAsService(service)
    }

    var level by funValue(FunLogLevel.Info)
    private val allTags = mutableStateSetOf<String>()
    val hiddenTags by funSet<String>(
        StringSetEditor(allTags)
    )
    var performance by funValue(true)

    override fun log(level: FunLogLevel, tag: String, exception: Throwable?, message: () -> String) {
        if (level >= this.level && tag !in hiddenTags) {
            log(tag, message)
            exception?.printStackTrace()
        }
    }

    override fun verbose(tag: String, message: () -> String) {
        log(FunLogLevel.Verbose, tag, null, message)
    }

    override fun debug(tag: String, message: () -> String) {
        log(FunLogLevel.Debug, tag, null, message)
    }

    override fun performance(tag: String, message: () -> String) {
        if (performance && tag !in hiddenTags) {
            log(tag, message)
        }
    }


    override fun info(tag: String, message: () -> String) {
        log(FunLogLevel.Info, tag, null, message)
    }

    override fun warn(tag: String, exception: Throwable?, message: () -> String) {
        log(FunLogLevel.Warn, tag, exception, message)
    }

    override fun error(tag: String, exception: Throwable?, message: () -> String) {
        if (FunLogLevel.Error >= this.level && tag !in hiddenTags) {
            allTags.add(tag)
            System.err.println("[$tag]: ${message()}")
            exception?.printStackTrace()
        }
    }

    private fun log(tag: String, message: () -> String) {
        allTags.add(tag)
        println("[$tag]: ${message()}")
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


fun getLogger() = FunContextRegistry.maybeGetContext()?.logger ?: SimpleLogger // Fallback to simple logger if there is no FunContext


fun FunContext.log(level: FunLogLevel, tag: String, exception: Throwable? = null, message: () -> String) {
    logger.log(level, tag, exception, message)
}

fun FunContext.logVerbose(tag: String, message: () -> String) {
    logger.verbose(tag,  message)
}

fun FunContext.logDebug(tag: String, message: () -> String) {
    logger.debug(tag, message)
}

fun FunContext.logPerformance(tag: String, message: () -> String) {
    logger.performance(tag, message)
}

fun FunContext.logInfo(tag: String, message: () -> String) {
    logger.info(tag, message)
}

fun FunContext.logWarn(tag: String, exception: Throwable? = null, message: () -> String) {
    logger.warn(tag, exception, message)
}

fun FunContext.logError(tag: String, exception: Throwable?, message: () -> String) {
    logger.error(tag, exception, message)
}

fun log(level: FunLogLevel, tag: String, exception: Throwable?, message: () -> String) {
    getLogger().log(level, tag, exception, message)
}

fun logVerbose(tag: String, message: () -> String) {
    getLogger().verbose(tag,  message)
}

fun logDebug(tag: String, message: () -> String) {
    getLogger().debug(tag, message)
}

fun logPerformance(tag: String, message: () -> String) {
    getLogger().performance(tag, message)
}

fun logInfo(tag: String, message: () -> String) {
    getLogger().info(tag, message)
}

fun logWarn(tag: String, exception: Throwable? = null, message: () -> String) {
    getLogger().warn(tag, exception, message)
}

fun logError(tag: String, exception: Throwable?, message: () -> String) {
    getLogger().error(tag, exception, message)
}