plugins {
    id("gradlebuild.platform")
}

description = "Defines which JARs go into the core part (libs/*.jar) of a Gradle distribution (NOT libs/plugins/*.jar)."

javaPlatform.allowDependencies()

dependencies {
    runtime(projects.installationBeacon)
    runtime(projects.apiMetadata)
    runtime(projects.baseDiagnostics)
    runtime(projects.daemonServer) {
        because("This is the Gradle daemon implementation, which transitively depends on all other core projects.")
    }
    runtime(projects.daemonMain) {
        because("This is the entry point of the Gradle daemon. It bootstraps the implementation.")
    }
    runtime(projects.gradleCli) {
        because("This is the `gradle` command implementation.")
    }
    runtime(projects.gradleCliMain) {
        because("This is the entry point of the `gradle` command. It bootstraps the implementation.")
    }
    runtime(projects.toolingApiProvider) {
        because("This is the entry point of the tooling API provider, which is the version-specific client part of the tooling API.")
    }
    runtime(projects.kotlinDsl) {
        because("Adds support for Kotlin DSL scripts.")
    }
    runtime(projects.declarativeDslProvider) {
        because("Adds support for interpreting files with the declarative DSL")
    }
}
