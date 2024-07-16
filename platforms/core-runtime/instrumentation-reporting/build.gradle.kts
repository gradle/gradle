plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains classes for instrumentation reporting, e.g. bytecode upgrades. " +
    "Note: For Configuration cache reporting see configuration-cache/input-tracking project."

dependencies {
    api(projects.internalInstrumentationApi)
    implementation(projects.stdlibJavaExtensions)
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
