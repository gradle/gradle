plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution abstractions."

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.messaging)

    api(libs.jsr305)

    implementation(projects.native)
    implementation(projects.serviceLookup)

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.nativePlatform)
    implementation(projects.internalInstrumentationApi)

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/process/internal/**")
}
