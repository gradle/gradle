plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Execution engine that takes a unit of work and makes it happen"

dependencies {
    api(libs.guava)
    api(libs.jsr305)
    api(libs.slf4jApi)

    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    compileOnly(libs.errorProneAnnotations)
    api(projects.baseServices)
    api(projects.buildCache)
    api(projects.buildCacheBase)
    api(projects.buildCacheSpi)
    api(projects.buildOperations)
    api(projects.coreApi)
    api(projects.files)
    api(projects.functional)
    api(projects.hashing)
    api(projects.modelCore)
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.snapshots)

    implementation(projects.time)
    implementation(projects.logging)
    implementation(projects.enterpriseOperations) {
        because("Adds generic build operations for the execution engine")
    }

    implementation(libs.commonsLang)
    implementation(libs.commonsIo)

    testImplementation(projects.native)
    testImplementation(projects.logging)
    testImplementation(projects.processServices)
    testImplementation(projects.baseServicesGroovy)
    testImplementation(projects.resources)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.buildOperations))
    testImplementation(testFixtures(projects.fileCollections))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.snapshots))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelCore))

    testFixturesImplementation(libs.guava)
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.buildCache)
    testFixturesImplementation(projects.problems)
    testFixturesImplementation(projects.snapshots)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
