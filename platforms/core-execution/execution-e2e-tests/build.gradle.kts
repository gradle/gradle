plugins {
    id("gradlebuild.internal.java")
}

description = "Execution engine end-to-end tests"

dependencies {
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

errorprone {
    nullawayEnabled = true
}
