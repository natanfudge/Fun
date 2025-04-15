package io.github.natanfudge.fu.hotreload

import com.sun.tools.attach.VirtualMachine
import io.github.natanfudge.fu.util.Observable
import io.github.natanfudge.fu.util.OwnedObservable
import java.io.FileOutputStream
import java.io.IOException
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.IllegalClassFormatException
import java.lang.instrument.Instrumentation
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.ProtectionDomain
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.exists
import kotlin.io.path.pathString


/**
 * Facilitates in-app detection of the JVM hot swap mechanism, allowing us to rerun code when hot swapping, making it more useful.
 */
object FunHotReload {
    val observation: Observable<Unit>
        internal field = OwnedObservable<Unit>()

    /**
     * Elaborate hack to detect when a JVM hotswap has occurred.
     * Requires setting the -Djdk.attach.allowAttachSelf=true VM option
     */
    fun detectHotswap() {
        // Generate agent JAR dynamically
        val agentJar = createAgentJar() ?: return

        // Attach the agent to the current JVM
        val pid = ManagementFactory.getRuntimeMXBean().name.split("@".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]
        try {
            val vm = VirtualMachine.attach(pid)
            vm.loadAgent(agentJar.toAbsolutePath().pathString)
            vm.detach()
        } catch (e: IOException) {
            println("Warning: Could not attach hot reload detection to the JVM, make sure the -Djdk.attach.allowAttachSelf=true VM option is set to enable enhanced hot reload.")
        }

    }
}




/**
 * Hooks into JVM transform calls to see when a class is being redefined.
 */
private class HotReloadDetectionTransformer : ClassFileTransformer {
    @Throws(IllegalClassFormatException::class)
    override fun transform(
        module: Module,
        loader: ClassLoader,
        className: String,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain,
        classfileBuffer: ByteArray,
    ): ByteArray? {
        if (classBeingRedefined != null) {
            FunHotReload.observation.emit(Unit)
        }
        // Return null to make no modifications to the class bytecode
        return null
    }
}

private object HotReloadDetectionAgent {
    @JvmStatic
    fun agentmain(args: String?, inst: Instrumentation?) {
        println("HotReloadDetectionAgent attached")
        inst?.addTransformer(HotReloadDetectionTransformer())
    }
}

@Throws(IOException::class)
private fun createAgentJar(): Path? {
    // Add your agent class file to the JAR
    val agentPath = "io/github/natanfudge/fu/hotreload/HotReloadDetectionAgent.class"
    val agentFile = Paths.get("build/classes/kotlin/main/$agentPath")
    if (!agentFile.exists()) {
        println("Cannot find agent class at $agentFile, advanced hot reload will not be available.")
        return null
    }
    val agentJarPath = Files.createTempFile("agent", ".jar")

    JarOutputStream(FileOutputStream(agentJarPath.toFile()), createManifest()).use { jos ->
        jos.putNextEntry(JarEntry(agentPath))
        Files.copy(agentFile, jos)
        jos.closeEntry()
    }
    return agentJarPath
}

private fun createManifest(): Manifest {
    val manifest = Manifest()
    manifest.mainAttributes.putValue("Manifest-Version", "1.0")
    manifest.mainAttributes.putValue("Agent-Class", "io.github.natanfudge.fu.hotreload.HotReloadDetectionAgent")
    manifest.mainAttributes.putValue("Can-Redefine-Classes", "true")
    manifest.mainAttributes.putValue("Can-Retransform-Classes", "true")
    return manifest
}
