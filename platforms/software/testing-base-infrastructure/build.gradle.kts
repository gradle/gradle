plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """Generalized test infrastructure to support executing tests in test workers."""

dependencies {
    api(projects.baseServices)
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.messaging)
    api(projects.serialization)
    api(projects.time)
    api(projects.workerMain)

    api(libs.jsr305)

    implementation(projects.io)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.commonsLang)
    implementation(libs.slf4jApi)

    testImplementation(projects.serviceRegistryImpl)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.serialization))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
