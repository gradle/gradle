plugins {
    id("gradlebuild.internal.java")
}

description = "Execution engine integration tests with Gradle Build Tool"

dependencies {
    integTestImplementation(project(":execution"))
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
