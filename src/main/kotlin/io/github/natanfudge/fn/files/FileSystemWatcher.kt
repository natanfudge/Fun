package io.github.natanfudge.fn.files

import java.nio.file.Path
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString


fun kotlinx.io.files.Path.toNio(): java.nio.file.Path = Paths.get(toString())
fun Path.toKotlin(): kotlinx.io.files.Path = kotlinx.io.files.Path(pathString)


class FileSystemWatcher(
    private val watchService: WatchService = FileSystems.getDefault().newWatchService()
) {

    fun close() {
        watchService.close()
    }

    /** Handle returned from [onFileChanged]; call [cancel] to stop listening. */
    inner class Key internal constructor(private val file: Path, private val cb: () -> Unit): AutoCloseable {
        /** Stop receiving events for this specific file-callback pair. */
        override fun close() {
            dirCallbacks[file.parent]?.let { list ->
                list.remove(cb)
                if (list.isEmpty()) {          // nothing left in this dir – deregister the watch key
                    watchKeys[file.parent]?.cancel()
                    watchKeys.remove(file.parent)
                    dirCallbacks.remove(file.parent)
                }
            }
        }
    }

    /* ---------- internal storage ---------- */

    private val watchKeys = ConcurrentHashMap<Path, WatchKey>()               // dir → WatchKey
    private val dirCallbacks = ConcurrentHashMap<Path, MutableList<() -> Unit>>() // dir → callback list map to _all_ files in that dir
    private val fileCallbacks = ConcurrentHashMap<Path, MutableList<() -> Unit>>() // full file → callbacks

    /* ---------- public API ---------- */

    /**
     * Register a callback that will be invoked when the underlying file changes
     * (CREATE, MODIFY, or DELETE events in its parent directory).
     */
    fun onFileChanged(path: kotlinx.io.files.Path, callback: () -> Unit): Key {
        val abs = path.toNio().toAbsolutePath().normalize()
        val dir = abs.parent ?: throw IllegalArgumentException("Path must have a parent directory")

        // keep per-file callback list
        fileCallbacks.computeIfAbsent(abs) { mutableListOf() }.add(callback)

        // make sure directory is registered exactly once
        if (watchKeys[dir] == null) {
            val key = dir.register(watchService, ENTRY_MODIFY)
            watchKeys[dir] = key
        }
        // keep list of callbacks per directory so we can remove quickly
        dirCallbacks.computeIfAbsent(dir) { mutableListOf() }.add(callback)

        return Key(abs, callback)
    }

    /**
     * Non-blocking dispatch: checks for pending file-system events and invokes
     * registered callbacks whose files were touched.
     *
     * Call this from a loop or timer;
     */
    fun poll() {
        var key: WatchKey? = watchService.poll()
        while (key != null) {
            val dir = key.watchable() as Path

            for (event in key.pollEvents()) {
                @Suppress("UNCHECKED_CAST")
                val ev = event as WatchEvent<Path>
                val changedFile = dir.resolve(ev.context()).toAbsolutePath().normalize()

                // invoke callbacks registered for *this exact file*
                fileCallbacks[changedFile]?.forEach { it.invoke() }
            }
            key.reset()
            key = watchService.poll()
        }
    }
}

