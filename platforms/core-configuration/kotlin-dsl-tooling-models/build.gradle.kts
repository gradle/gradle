plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Kotlin DSL Tooling Models for IDEs (Legacy)"

dependencies {
    api(libs.jspecify)
}

// Kotlin DSL tooling models should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())

errorprone {
    nullawayEnabled = true
}
