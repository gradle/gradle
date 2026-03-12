plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal interfaces and implementations for input normalization"

dependencies {
    api(projects.normalizationApi)
    api(projects.baseServices)
    api(projects.snapshots)
    api(projects.hashing)
    api(projects.files)
    api(projects.serviceProvider)

    api(libs.jspecify)

    implementation(libs.guava)

    testImplementation(projects.internalTesting)

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}
