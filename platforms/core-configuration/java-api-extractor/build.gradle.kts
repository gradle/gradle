plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Logic to extract API classes from JVM classes that is shared between build-logic and runtime."

dependencies {
    api(libs.asm)
    api(libs.guava)
}

packageCycles {
    excludePatterns.add("org/gradle/internal/tools/api/impl/*")
}

// Should not be part of the public API
// TODO Find a proper way to configure this
configurations.remove(configurations.apiStubElements.get())
