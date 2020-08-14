// Defines which JARs go into the core part (libs/*.jar) of a Gradle distribution (NOT libs/plugins/*.jar).
plugins {
    id("gradlebuild.platform")
}

javaPlatform.allowDependencies()

dependencies {
    runtime(project(":installation-beacon"))
    runtime(project(":api-metadata"))
    runtime(project(":launcher")) {
        because("This is the entry point of Gradle core which transitively depends on all other core projects.")
    }
    runtime(project(":kotlin-dsl")) {
        because("Adds support for Kotlin DSL scripts.")
    }
}
