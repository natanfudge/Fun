@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.compose.reload.gradle.ComposeHotRun
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.graalvm.buildtools.native") version "0.10.6"
    id("com.google.osdetector") version "1.7.3"
    id("org.jetbrains.compose.hot-reload") version "1.0.0-beta04"
}


repositories {
    maven("https://packages.jetbrains.team/maven/p/firework/dev")
}

//val mainclass = "io.github.natanfudge.fn.GuiMainKt"
//val mainclass = "io.github.natanfudge.fn.core.newstuff.NewFunContextKt"
val mainclass = "io.github.natanfudge.fn.mte.DeepSoulsKt"
graalvmNative {
    binaries {
        all {
            resources.autodetect()
        }
        named("main") {
            mainClass.set(mainclass)
            imageName.set("Fun-Native")
            buildArgs(
                "-Djava.awt.headless=false",
                // Development only
//                "-Ob",
            )
            val isWindows: Boolean by extra { osdetector.os == "windows" }
            if (isWindows) {
                buildArgs(
                    "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
                    "-H:NativeLinkerOption=/ENTRY:mainCRTStartup"
                )

            }
        }

        agent {
            enabled.set(false)
            metadataCopy {
                outputDirectories.add("src/main/resources/META-INF/native-image/io.github.natanfudge/fun")
            }
        }
    }
}


tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set(mainclass)
    jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005")
}

//tasks.withType<ComposeHotRun>().configureEach {
//    mainClass.set("io.github.natanfudge.fn.MainKt")
////    mainClass.set("io.github.natanfudge.fn.compose.utils.FloatFieldKt")
//
//}

group = "natan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    //wgpu4k snapshot & preview repository
    maven("https://gitlab.com/api/v4/projects/25805863/packages/maven")
}




dependencies {
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.bundles.jvmMain)
    implementation(libs.bundles.commonMain)

    compileOnly(libs.bundles.jvmMainCompileOnly)
    implementation("org.jetbrains.compose.hot-reload:hot-reload-runtime-api:1.0.0-beta04")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-agent:1.0.0-beta04")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-core:1.0.0-beta04")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-orchestration:1.0.0-beta04")
    implementation("org.jetbrains.compose.hot-reload:hot-reload-analysis:1.0.0-beta04")
    implementation(kotlin("reflect"))

    implementation(libs.bundles.lwjgl)
    runtimeOnly(libs.bundles.lwjgl) {
        artifact {
            classifier = "natives-windows"
        }
    }

    val koin_version = "4.0.3"
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(project.dependencies.platform("io.insert-koin:koin-bom:$koin_version"))
    implementation("io.insert-koin:koin-core")
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")
    implementation("io.github.natanfudge:wgpu4k-matrix:0.7.1")
    implementation("com.soywiz:korlibs-image:6.0.0")
    implementation(libs.wgpu4k)
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
//    implementation(group = "com.patrykandpatrick.vico", name = "multiplatform", version = "2.1.3")

    runtimeOnly(compose.desktop.windows_x64)

    testImplementation(libs.bundles.commonTest)
    testImplementation(libs.bundles.jvmTest)
}



kotlin {
    jvmToolchain {
        this.languageVersion = JavaLanguageVersion.of(22)
//        installationPath.set(rootProject.layout.projectDirectory.dir("jdks/jbr_25_11_05"))

//        vendor = JvmVendorSpec.JETBRAINS
    }
//    sourceSets.all {
//        languageSettings.enableLanguageFeature("ExplicitBackingFields")
//    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xwhen-guards")
    }
}


tasks.register<Jar>("fatJar") {
    dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources")) // add this task's dependencies
    archiveClassifier.set("fat") // adds 'fat' to the jar name
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = mainclass // replace with your actual main class
    }

    from(sourceSets.main.get().output)

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}


compose.desktop {
    application {
        this.mainClass = mainclass
    }

}

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}

tasks.withType<Test> {
    val jdksDir = rootProject.layout.projectDirectory.dir("jdks").asFile
    if (jdksDir.exists()) {
        val specificJdk = jdksDir.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("jbr") }
        if (specificJdk != null) {
            val exe = specificJdk.resolve("bin/java.exe")
            println("Use JBR 25: $exe")
            executable = exe.toString()
            jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:HotswapAgent=core", "--enable-native-access=ALL-UNNAMED")
        } else {
            println("Warn: no JBR under jdks/")
        }
    } else {
        println("Warn: missing jdks dir")
    }

    useJUnitPlatform()
    maxParallelForks = 1
    testLogging { // credits: https://stackoverflow.com/a/36130467/5917497
        // set options for log level LIFECYCLE
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT
        )
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        // set options for log level DEBUG and INFO
        debug {
            events = setOf(
                TestLogEvent.STARTED,
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_ERROR,
                TestLogEvent.STANDARD_OUT
            )
            exceptionFormat = TestExceptionFormat.FULL
        }
        info.events = debug.events
        info.exceptionFormat = debug.exceptionFormat
        afterSuite(KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            if (desc.parent == null) { // will match the outermost suite
                val pass = "${Color.GREEN}${result.successfulTestCount} passed${Color.NONE}"
                val fail = "${Color.RED}${result.failedTestCount} failed${Color.NONE}"
                val skip = "${Color.YELLOW}${result.skippedTestCount} skipped${Color.NONE}"
                val type = when (val r = result.resultType) {
                    TestResult.ResultType.SUCCESS -> "${Color.GREEN}$r${Color.NONE}"
                    TestResult.ResultType.FAILURE -> "${Color.RED}$r${Color.NONE}"
                    TestResult.ResultType.SKIPPED -> "${Color.YELLOW}$r${Color.NONE}"
                }
                val output = "Results: $type (${result.testCount} tests, $pass, $fail, $skip)"
                val startItem = "|   "
                val endItem = "   |"
                val repeatLength = startItem.length + output.length + endItem.length - 36
                println("")
                println("\n" + ("-" * repeatLength) + "\n" + startItem + output + endItem + "\n" + ("-" * repeatLength))
            }
        }))
    }
    onOutput(KotlinClosure2({ _: TestDescriptor, event: TestOutputEvent ->
        if (event.destination == TestOutputEvent.Destination.StdOut) {
            logger.lifecycle(event.message.replace(Regex("""\s+$"""), ""))
        }
    }))
}

operator fun String.times(x: Int): String {
    return List(x) { this }.joinToString("")
}

internal enum class Color(ansiCode: Int) {
    NONE(0),
    BLACK(30),
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    PURPLE(35),
    CYAN(36),
    WHITE(37);

    private val ansiString: String = "\u001B[${ansiCode}m"

    override fun toString(): String {
        return ansiString
    }
}


//tasks["processResources"].doLast {
//    val composeResourcesDir = project.layout.buildDirectory.file("resources/main/composeResources")
//
//    val children = composeResourcesDir.get().asFile.listFiles()
//    if (children.size != 1) {
//        error("Expected only one directory under $composeResourcesDir, actual: $children")
//    }
//    val projectSpecificDir = children.single()
//    val drawable = projectSpecificDir.resolve("drawable")
//    val allNested = drawable.listFiles().filter { it.isDirectory }
//
//    for (nested in allNested) {
//        nested.walkTopDown().forEach {
//            if (it.isDirectory) return@forEach
//            val relative = it.relativeTo(drawable)
//            val flattened = relative.toString().replace(Regex("""[/\\]"""), "_")
//            it.copyTo(drawable.resolve(flattened), overwrite = true)
//        }
//        nested.deleteRecursively()
//    }
//}


// This task configuration will process files in `src/main/composeResources/drawable`
//tasks.register<Copy>("flattenComposeResources") {
//    val sourceDir = File(layout.projectDirectory.get().asFile, "ui_resources")
//    val targetDir = File(layout.projectDirectory.get().asFile, "my-app/src/commonMain/composeResources/drawable")
//
//    from(sourceDir)
//    into(targetDir)
//

//
//    includeEmptyDirs = false
//}