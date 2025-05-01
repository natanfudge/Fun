package io.github.natanfudge.fn.files

import kotlinx.io.files.Path
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.WatchKey
import kotlin.io.path.toPath


fun kotlinx.io.files.Path.toNio(): java.nio.file.Path = Paths.get(toString())

/**
 * There is no kotlin multiplatform URI yet
 */
typealias KotlinURI = String
// SLOW: kind of an awkward way of doing this
fun KotlinURI.uriParentDirectory() = URI.create(this).toPath().parent.toUri().toString()



class FileSystemWatcher: AutoCloseable {
    private val service = FileSystems.getDefault().newWatchService()

    private val watchKeys = mutableMapOf<WatchKey, () -> Unit>()

    inner class Key(
        val key: WatchKey
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
        val key =  path.toNio().register(service, StandardWatchEventKinds.ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE)

        watchKeys[key] = callback
        return Key(key)
    }


    // SLOW: it's best to run this on a different thread and allow it to block until a new message is received, but this works for now
    fun poll() {
        val key = service.poll()
        if (key != null) {
            val callback = watchKeys[key] ?: error("Missing callback for registered key $key")
            callback()
            key.reset() // We still want to hear from this event
        }
    }

    override fun close() {
        service.close()
    }
}

