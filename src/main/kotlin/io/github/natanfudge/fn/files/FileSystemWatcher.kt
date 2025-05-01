package io.github.natanfudge.fn.files

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
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
    fun onDirectoryChanged(directoryUri: KotlinURI, callback: () -> Unit): Key {
        val path = URI.create(directoryUri)
        val key =  path.toPath().register(service, StandardWatchEventKinds.ENTRY_MODIFY)

        //TODO: i'm watching for the build path, need to watch for the source path
        watchKeys[key] = callback
        return Key(key)
    }


    // SLOW: it's best to run this on a different thread and allow it to block until a new message is received, but this works for now
    fun poll() {

        val key = service.poll()
        if (key != null) {
            val callback = watchKeys[key] ?: error("Missing callback for registered key $key")
            callback()
        }
    }

    override fun close() {
        service.close()
    }
}

