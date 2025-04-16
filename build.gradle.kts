plugins {
    kotlin("jvm") version "2.1.20"
}

group = "natan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.bundles.jvmMain)
    implementation(libs.bundles.commonMain)

    compileOnly("org.hotswapagent:hotswap-agent-core:2.0.2")

    runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl.get()}:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:${libs.versions.lwjgl.get()}:natives-windows")
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
