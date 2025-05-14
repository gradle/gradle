plugins {
    id("java-library")
    id("org.jetbrains.kotlin.plugin.serialization") // Now the plugin is applied
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
