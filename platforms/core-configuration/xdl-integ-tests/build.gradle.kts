plugins {
    id("gradlebuild.internal.java")
}

description = "XDL (.gradle.xdl) scripting language integration tests"

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
