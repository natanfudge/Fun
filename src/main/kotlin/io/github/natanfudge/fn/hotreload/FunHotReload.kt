package io.github.natanfudge.fn.hotreload

import io.github.natanfudge.fn.util.MutEventStream
import org.jetbrains.compose.reload.agent.Reload
import org.jetbrains.compose.reload.core.Try

// To sum up the current commandments of DCEVM:
// 1. Thou shalt not keep changing code next to long-running code: https://github.com/JetBrains/JetBrainsRuntime/issues/534
// 2. Thou shalt not store a lambda in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/535
// 3. Thou shalt not store an anonymous class in memory without recreating it on reload: https://github.com/JetBrains/JetBrainsRuntime/issues/536

/**
 * Current limitation - if the schema of lifecycle state changes, hot reloading might fail.
 * Facilitates in-app detection of the JVM hot swap mechanism, allowing us to rerun code when hot swapping, making it more useful.
 */
object FunHotReload {
//    val observation: Observable<Unit>
    /**
     * The callback will be called on an alternate thread so be wary of that.
     * If you want it to run code on the main thread you can use [io.github.natanfudge.fn.compose.ComposeOpenGLRenderer.submitTask]
     */
    val reloadEnded = MutEventStream<Try<Reload>?>()
    val reloadStarted = MutEventStream<Unit>()


//    fun observe(callback: (Unit) -> Unit) = reloadEnded.listen(callback)

    /**
     * Called externally by Hotswap Agent
     */
    internal fun notifyReload() {
        reloadEnded.emit(null)
    }

//    /**
//     * Elaborate hack to detect when a JVM hotswap has occurred.
//     * Requires setting the -Djdk.attach.allowAttachSelf=true VM option
//     */
//    fun detectHotswap() {
//        // Generate agent JAR dynamically
//        val agentJar = createAgentJar() ?: return
//
//        // Attach the agent to the current JVM
//        val pid = ManagementFactory.getRuntimeMXBean().name.split("@".toRegex()).dropLastWhile { it.isEmpty() }
//            .toTypedArray()[0]
//        try {
//            val vm = VirtualMachine.attach(pid)
//            vm.loadAgent(agentJar.toAbsolutePath().pathString)
//            vm.detach()
//        } catch (e: IOException) {
//            println("Warning: Could not attach hot reload detection to the JVM, make sure the -Djdk.attach.allowAttachSelf=true VM option is set to enable enhanced hot reload.")
//        }
//
//    }
}



//
///**
// * Hooks into JVM transform calls to see when a class is being redefined.
// */
//private class HotReloadDetectionTransformer : ClassFileTransformer {
//    // Track if we're currently in a debounce period
//    private var debounceScheduled = false
//
//    @Throws(IllegalClassFormatException::class)
//    override fun transform(
//        module: Module,
//        loader: ClassLoader,
//        className: String,
//        classBeingRedefined: Class<*>?,
//        protectionDomain: ProtectionDomain,
//        classfileBuffer: ByteArray,
//    ): ByteArray? {
//        if (classBeingRedefined != null && !debounceScheduled) {
//            debounceScheduled = true
//
//            // Schedule a delayed emission
//            GlobalScope.launch {
//                // Wait for all transformations in this batch to complete
//                delay(100) // This works for now but might not work with larger projects
//
//                // Emit only once after the debounce period
//                FunHotReload.observation.emit(Unit)
//
//                // Reset the flag for the next reload
//                debounceScheduled = false
//            }
//        }
//        // Return null to make no modifications to the class bytecode
//        return null
//    }
//}
//
//
//private object HotReloadDetectionAgent {
//    @JvmStatic
//    fun agentmain(args: String?, inst: Instrumentation?) {
//        println("HotReloadDetectionAgent attached")
//        inst?.addTransformer(HotReloadDetectionTransformer())
//    }
//}
//
//@Throws(IOException::class)
//private fun createAgentJar(): Path? {
//    // Add your agent class file to the JAR
//    val agentPath = "io/github/natanfudge/fu/hotreload/HotReloadDetectionAgent.class"
//    val agentFile = Paths.get("build/classes/kotlin/main/$agentPath")
//    if (!agentFile.exists()) {
//        println("Cannot find agent class at $agentFile, advanced hot reload will not be available.")
//        return null
//    }
//    val agentJarPath = Files.createTempFile("agent", ".jar")
//
//    JarOutputStream(FileOutputStream(agentJarPath.toFile()), createManifest()).use { jos ->
//        jos.putNextEntry(JarEntry(agentPath))
//        Files.copy(agentFile, jos)
//        jos.closeEntry()
//    }
//    return agentJarPath
//}
//
//private fun createManifest(): Manifest {
//    val manifest = Manifest()
//    manifest.mainAttributes.putValue("Manifest-Version", "1.0")
//    manifest.mainAttributes.putValue("Agent-Class", "io.github.natanfudge.fu.hotreload.HotReloadDetectionAgent")
//    manifest.mainAttributes.putValue("Can-Redefine-Classes", "true")
//    manifest.mainAttributes.putValue("Can-Retransform-Classes", "true")
//    return manifest
//}
