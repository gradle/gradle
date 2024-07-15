plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains classes for instrumentation reporting, e.g. bytecode upgrades. " +
    "Note: For Configuration cache reporting see configuration-cache/input-tracking project."

dependencies {
    implementation(projects.stdlibJavaExtensions)
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
