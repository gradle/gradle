plugins {
    id("gradlebuild.platform")
}

description = "Defines which JARs go into the core part (libs/*.jar) of a Gradle distribution (NOT libs/plugins/*.jar)."

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
    runtime(project(":restricted-dsl")) {
        because("Adds support for interpreting files with the restricted DSL")
    }
}
