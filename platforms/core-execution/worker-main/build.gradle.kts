plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Infrastructure that bootstraps a worker process"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.problemsApi)
    api(projects.processMemoryServices)
    api(projects.native)
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(projects.enterpriseLogging)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
