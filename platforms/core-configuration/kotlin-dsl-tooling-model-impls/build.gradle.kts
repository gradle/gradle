plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Implementations of Kotlin DSL Tooling API models"

dependencies {
    api(projects.toolingApi)
    api(projects.kotlinDslToolingModels)
    api(libs.kotlinStdlib)
}

gradleModule {
    computedRuntimes {
    }
}
