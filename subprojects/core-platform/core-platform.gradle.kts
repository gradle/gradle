// Defines which JARs go into the core part (libs/*.jar) of a Gradle distribution (NOT libs/plugins/*.jar).
plugins {
    gradlebuild.platform
}

javaPlatform.allowDependencies()

dependencies {
    runtime(project(":installationBeacon"))
    runtime(project(":apiMetadata"))
    runtime(project(":launcher")) {
        because("This is the entry point of Gradle core which transitively depends on all other core projects.")
    }
    runtime(project(":kotlinDsl")) {
        because("Adds support for Kotlin DSL scripts.")
    }
}
