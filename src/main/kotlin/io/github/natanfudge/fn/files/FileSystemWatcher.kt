package io.github.natanfudge.fn.files

import kotlinx.io.files.Path
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey


fun kotlinx.io.files.Path.toNio(): java.nio.file.Path = Paths.get(toString())


/**
 * You should use only once instance of this, as it behaves weirdly when you try to register the same path twice in different parts of the code
 */
class FileSystemWatcher : AutoCloseable {
    private val service = FileSystems.getDefault().newWatchService()

    private val watchKeys = mutableMapOf<WatchKey, MutableList<() -> Unit>>()
    private val registeredPaths = mutableSetOf<Path>()

    inner class Key(
        val key: WatchKey,
        val path: Path,
    ) {
        fun close() {
            key.cancel()
            watchKeys.remove(key)
        }
    }


    /**
     * You must call [poll] continuously for this function to work (on most platforms)
     * [callback] will be called when the contents of [directoryUri] changes, on the same thread that [poll] is called on
     * [Key.close] should be called on the returned value to stop listening to file changes for that path.
     */
    fun onDirectoryChanged(path: Path, callback: () -> Unit): Key {
        val key = path.toNio().register(service, StandardWatchEventKinds.ENTRY_MODIFY)

        if (path !in registeredPaths) {
            watchKeys[key] = mutableListOf(callback)
            registeredPaths.add(path)
        } else {
            // If it's in the registered paths, we can add it to the list of listeners for this path
            watchKeys.getValue(key).add(callback)
        }

        return Key(key, path)
    }


    // SLOW: it's best to run this on a different thread and allow it to block until a new message is received, but this works for now
    fun poll() {
        val key = service.poll()
        if (key != null) {
            key.pollEvents()
            val callback = watchKeys[key] ?: error("Missing callback for registered key $key")
            callback.forEach { it() }
            key.reset() // We still want to hear from this event
        }
    }

    override fun close() {
        service.close()
    }
}

