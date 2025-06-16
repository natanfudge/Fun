package io.github.natanfudge.fn.compose.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Debounces calls to [submit].  Only the *last* call made within [delayMillis]
 * is executed, and it runs on [scope].
 *
 * Example:
 * ```kotlin
 * val debouncer = Debouncer(300, viewModelScope)   // 300 ms debounce
 * searchBox.textChanges   // however you expose your text changes
 *     .onEach { query -> debouncer.submit { viewModel.search(query) } }
 * ```
 */
class Debouncer(
    private val delayMillis: Duration,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    /** Enqueue a block. Any previously enqueued block is cancelled. */
    fun submit(block: () -> Unit) {
        job?.cancel()                // throw away the previous attempt
        job = scope.launch {
            delay(delayMillis)       // wait out the “quiet period”
            block()                  // run the latest request
        }
    }

    /** Cancel the pending block, if any. Call from `onDestroy()` / `dispose()` if needed. */
    fun cancel() = job?.cancel()
}