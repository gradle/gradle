plugins {
    id("gradlebuild.distribution.api-kotlin")
}

description = "Kotlin DSL Tooling Models for IDEs"

dependencies {
    api(project(":base-annotations"))
}
