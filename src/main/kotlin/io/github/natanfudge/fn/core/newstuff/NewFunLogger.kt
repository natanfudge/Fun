package io.github.natanfudge.fn.core.newstuff

import androidx.compose.runtime.mutableStateSetOf
import io.github.natanfudge.fn.compose.funedit.StringSetEditor
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunLogLevel
import io.github.natanfudge.fn.core.ILogger
import io.github.natanfudge.fn.network.state.funSet
import io.github.natanfudge.fn.network.state.funValue

class NewFunLogger : NewFun("FunLogger", Unit), ILogger {
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
