plugins {
    id("gradlebuild.internal.java")
}

description = "Execution engine end-to-end tests"

dependencies {
    integTestImplementation(projects.execution)
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
