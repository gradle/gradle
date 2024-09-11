plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.api-metadata")
}

description = "Generated metadata about Gradle API needed by Kotlin DSL"
tasks.isolatedProjectsIntegTest {
    enabled = false
}
