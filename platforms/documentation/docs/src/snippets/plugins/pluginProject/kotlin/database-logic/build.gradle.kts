plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.1.21-RC"
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(11))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

// More build logic
