plugins {
    id("gradlebuild.internal.java")
}

description = "Execution engine end-to-end tests"

dependencies {
    integTestImplementation(project(":execution"))
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
