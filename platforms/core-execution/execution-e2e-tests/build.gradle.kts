plugins {
    id("gradlebuild.internal.java")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Execution engine end-to-end tests"

dependencies {
    integTestImplementation(projects.execution)
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

errorprone {
    nullawayEnabled = true
}
