pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        maven ("https://packages.jetbrains.team/maven/p/firework/dev")
    }
}
plugins {
    id ("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    id ("de.fayard.refreshVersions") version "0.60.5"
}
rootProject.name = "Fun"

val linkMatrix = true
val matrixDir = file("../wgpu4k-matrix")
if (linkMatrix) {
    includeBuild(matrixDir) {
        dependencySubstitution {
            substitute(module("io.github.natanfudge:wgpu4k-matrix")).using(project(":"))
        }
    }
}