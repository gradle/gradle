plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Process memory abstractions."

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

    testImplementation(testFixtures(projects.core))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/process/internal/**")
}
