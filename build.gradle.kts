plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.power.assert)
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
        this.languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.JETBRAINS
    }
//    jvmToolchain(21)
    sourceSets.all {
        languageSettings.enableLanguageFeature("ExplicitBackingFields")
    }
}

tasks.register<Jar>("fatJar") {
    dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources")) // add this task's dependencies
    archiveClassifier.set("fat") // adds 'fat' to the jar name
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.github.natanfudge.fu.MainKt" // replace with your actual main class
    }

    from(sourceSets.main.get().output)

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}