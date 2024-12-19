plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Kotlin DSL Tooling Models for IDEs"

dependencies {
    api(libs.jsr305)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
