plugins {
    gradlebuild.internal.java
    gradlebuild.distributions
}

dependencies {
    runtimeOnly(project(":installationBeacon"))
    runtimeOnly(project(":runtimeApiInfo"))
    runtimeOnly(project(":apiMetadata"))
    runtimeOnly(project(":docs")) // TODO get rid of docs.jar and move the needed resources into one of the other jars
    runtimeOnly(project(":launcher")) {
        because("This is the entry point of Gradle core which which transitively depends on all other core projects.")
    }
    runtimeOnly(project(":kotlinDsl")) {
        because("Adds support for Kotlin DSL scripts")
    }
}
