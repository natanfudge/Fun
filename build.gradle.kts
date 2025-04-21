@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.register

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.graalvm.buildtools.native") version "0.10.6"
    id("com.google.osdetector") version "1.7.3"
}


val mainclass = "io.github.natanfudge.fn.MainKt"
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

group = "natan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}




dependencies {
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.bundles.jvmMain)
    implementation(libs.bundles.commonMain)

    compileOnly(libs.bundles.jvmMainCompileOnly)

    implementation(libs.bundles.lwjgl)
    runtimeOnly(libs.bundles.lwjgl) {
        artifact {
            classifier = "natives-windows"
        }
    }

    val koin_version = "4.0.3"
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(project.dependencies.platform("io.insert-koin:koin-bom:$koin_version"))
    implementation("io.insert-koin:koin-core")
    runtimeOnly(compose.desktop.windows_x64)

    testImplementation(libs.bundles.commonTest)
}

kotlin {
    jvmToolchain {
        this.languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.JETBRAINS
    }
    sourceSets.all {
        languageSettings.enableLanguageFeature("ExplicitBackingFields")
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

tasks.withType<Test>() {
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