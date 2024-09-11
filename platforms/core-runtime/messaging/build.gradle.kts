plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of messaging between Gradle processes"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(projects.baseServices)

    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.io)
    implementation(projects.buildOperations)

    implementation(libs.guava)

    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.core))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
    integTestImplementation(projects.serviceRegistryBuilder)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
