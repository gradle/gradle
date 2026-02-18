plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Implementations of Kotlin DSL Tooling API models"

dependencies {
    api(projects.toolingApi)
    api(libs.kotlinStdlib)
}
