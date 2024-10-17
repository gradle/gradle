plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains classes for instrumentation reporting, e.g. bytecode upgrades. " +
    "Note: For Configuration cache reporting see configuration-cache/input-tracking project."

dependencies {
    api(projects.internalInstrumentationApi)
    api(projects.stdlibJavaExtensions)
    api(libs.jsr305)
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
