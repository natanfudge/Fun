plugins {
    kotlin("jvm") version "2.1.20"
}

group = "natan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Workaround for https://youtrack.jetbrains.com/issue/KTIJ-8414/Rendered-KDoc-Quick-Doc-popup-or-Reader-mode-for-sample-which-references-code-in-another-package-is-shown-as-Unresolved
//    testImplementation ("org.jetbrains.kotlin:kotlin-test")
    implementation ("org.jetbrains.kotlin:kotlin-test")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
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