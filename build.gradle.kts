plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.power.assert)
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

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    runtimeOnly(compose.desktop.windows_x64)
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Include tests from main source set
    testClassesDirs = files(
        testClassesDirs,
        project.sourceSets.main.get().output.classesDirs
    )
    classpath = files(
        classpath,
        project.sourceSets.main.get().runtimeClasspath
    )
}

kotlin {
    jvmToolchain {
//        this.languageVersion = JavaLanguageVersion.of(21)
//        vendor = JvmVendorSpec.JETBRAINS
        this.languageVersion = JavaLanguageVersion.of(23)
        vendor = JvmVendorSpec.GRAAL_VM
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