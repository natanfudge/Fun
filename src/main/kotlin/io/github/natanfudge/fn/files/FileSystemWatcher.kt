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

    /** Handle returned from [onFileChanged]; call [cancel] to stop listening. */
    inner class Key internal constructor(private val file: Path, private val cb: () -> Unit) {
        /** Stop receiving events for this specific file-callback pair. */
        fun cancel() {
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


//
///**
// * You should use only once instance of this, as it behaves weirdly when you try to register the same path twice in different parts of the code
// */
//class FileSystemWatcher : AutoCloseable {
//    private val service = FileSystems.getDefault().newWatchService()
//
//    private val watchKeys = mutableMapOf<WatchKey, MutableList<() -> Unit>>()
//    private val registeredPaths = mutableMapOf<Path, () -> Unit>()
//
//    //TODO finish this, make sure to close the key properly, we need to check if there are
////    private val registeredPaths = mutableSetOf<Path>()
//
//    inner class Key(
//        val key: WatchKey,
//        val path: Path,
//    ) {
//        //TODO: maybe create a DirectoryWatcher kind of thing to make this simpler
//        fun close() {
////            key.cancel()
////            watchKeys.remove(key)
//        }
//    }
//
//    fun onFileChanged(path: Path, callback: () -> Unit): Key {
//        registeredPaths[path] = callback
//        val parent = path.parent
//        requireNotNull(parent)
//
//        //TODO
////        if(parent path not registered) {
////            register parent path
////        }
//
//
//    }
//
//
//    /**
//     * You must call [poll] continuously for this function to work (on most platforms)
//     * [callback] will be called when the contents of [directoryUri] changes, on the same thread that [poll] is called on
//     * [Key.close] should be called on the returned value to stop listening to file changes for that path.
//     */
//    fun onDirectoryChanged(path: Path, callback: () -> Unit): Key {
//        val key = path.toNio().register(service, StandardWatchEventKinds.ENTRY_MODIFY)
//
//        if (path !in registeredPaths) {
//            watchKeys[key] = mutableListOf(callback)
//            registeredPaths.add(path)
//        } else {
//            // If it's in the registered paths, we can add it to the list of listeners for this path
//            watchKeys.getValue(key).add(callback)
//        }
//
//        return Key(key, path)
//    }
//
//
//    // SLOW: it's best to run this on a different thread and allow it to block until a new message is received, but this works for now
//    fun poll() {
//        val key = service.poll()
//        if (key != null) {
//            val events = key.pollEvents()
//            for(event in events) {
//                if(event path is one of registered events) {
//                    invoke event
//                }
//            }
//
////            println("Event files: ${events.map { it.context() }}")
////            val callback = watchKeys[key] ?: error("Missing callback for registered key $key")
////            callback.forEach { it() }
//            key.reset() // We still want to hear from this event
//        }
//    }
//
//    override fun close() {
//        service.close()
//    }
//}
//
