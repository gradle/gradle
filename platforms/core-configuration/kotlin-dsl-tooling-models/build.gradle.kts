plugins {
    id("gradlebuild.distribution.api-kotlin")
}

description = "Kotlin DSL Tooling Models for IDEs"

dependencies {
    api(libs.jsr305)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
