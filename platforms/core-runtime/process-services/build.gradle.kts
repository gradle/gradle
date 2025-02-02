plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution abstractions."

dependencies {
    api(projects.concurrent)
    api(projects.baseServices)
    api(projects.coreApi)
    api(libs.jsr305)

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/process/internal/**")
}
