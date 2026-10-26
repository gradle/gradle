plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm") version "2.5.20-dev-2076"
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
