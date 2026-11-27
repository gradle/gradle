plugins {
    id("gradlebuild.internal.java")
}

description = "Execution engine end-to-end tests"

dependencies {
    integTestDistributionRuntimeOnly(projects.distributionsFull)

    integTestImplementation(projects.baseServices)
    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.execution)
    integTestImplementation(libs.groovy)
    integTestImplementation(libs.guava)
    integTestImplementation(testLibs.spock)
}

errorprone {
    nullawayEnabled = true
}
