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

    runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:${libs.versions.lwjgl.get()}:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:${libs.versions.lwjgl.get()}:natives-windows")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
    sourceSets.all {
        languageSettings.enableLanguageFeature("ExplicitBackingFields")
    }
}