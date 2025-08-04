package io.github.natanfudge.fn.test.util

import io.github.natanfudge.fn.files.FileSystemWatcher
import io.github.natanfudge.fn.files.toKotlin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun waitForLatch(
    watcher: FileSystemWatcher,
    latch: CountDownLatch,
    timeoutSeconds: Long = 2,
    pollDelayMillis: Long = 10
) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    while (latch.count > 0 && System.nanoTime() < deadline) {
        watcher.poll()
        Thread.sleep(pollDelayMillis)
    }
}


/** All tests use an isolated temporary directory supplied by JUnit 5. */
class FileSystemWatcherTest {

    @Test
    fun `callback fires when file is modified`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("file.txt").also { Files.writeString(it, "") }

        val watcher = FileSystemWatcher()
        val latch = CountDownLatch(1)
        watcher.onFileChanged(file.toKotlin()) { latch.countDown() }

        // Touch the file
        Files.writeString(
            file, "first change\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        )

        waitForLatch(watcher, latch)
        assertEquals(0, latch.count, "Callback should have executed")
    }

    @Test
    fun `callback is NOT fired for unrelated file`(@TempDir tempDir: Path) {
        val watched = tempDir.resolve("watched.txt").also { Files.writeString(it, "") }
        val other   = tempDir.resolve("other.txt").also   { Files.writeString(it, "") }

        val watcher = FileSystemWatcher()
        val latch = CountDownLatch(1)
        watcher.onFileChanged(watched.toKotlin()) { latch.countDown() }

        // Modify a different file
        Files.writeString(
            other, "unrelated change\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND
        )

        waitForLatch(watcher, latch, timeoutSeconds = 1)
        assertEquals(1, latch.count, "Callback should NOT have executed")
    }

    @Test
    fun `cancel prevents further notifications`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("data.txt").also { Files.writeString(it, "") }

        val watcher = FileSystemWatcher()
        val firstLatch  = CountDownLatch(1)
        val key = watcher.onFileChanged(file.toKotlin()) { firstLatch.countDown() }

        // 1st change → should trigger
        Files.writeString(file, "change1\n", StandardOpenOption.APPEND)
        waitForLatch(watcher, firstLatch)
        assertEquals(0, firstLatch.count, "First callback should fire")

        // Cancel the registration
        key.close()

        // 2nd change → should NOT trigger
        val secondLatch = CountDownLatch(1)
        Files.writeString(file, "change2\n", StandardOpenOption.APPEND)
        waitForLatch(watcher, secondLatch, timeoutSeconds = 1)
        assertEquals(1, secondLatch.count, "Callback must not fire after cancel")
    }

    @Test
    fun `all callbacks on same file are invoked`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("shared.txt").also { Files.writeString(it, "") }

        val watcher = FileSystemWatcher()
        val latch = CountDownLatch(3)

        repeat(3) { watcher.onFileChanged(file.toKotlin()) { latch.countDown() } }

        Files.writeString(file, "update\n", StandardOpenOption.APPEND)

        waitForLatch(watcher, latch)
        assertEquals(0, latch.count, "Every callback registered for the file must run")
    }
}
