plugins {
    id("gradlebuild.internal.java")
}

description = "XDCL (.gradle.xdcl) scripting language integration tests"

dependencies {
    integTestImplementation(projects.internalTesting)
    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.logging)
    integTestImplementation(projects.coreApi)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}

errorprone {
    nullawayEnabled = true
}
