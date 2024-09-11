plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.jmh")
}

description = "Local build cache implementation"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.buildCache)
    api(projects.buildCacheSpi)
    api(projects.files)
    api(projects.functional)
    api(projects.hashing)
    api(projects.persistentCache)

    implementation(libs.commonsIo)
    implementation(libs.guava)

    testImplementation(projects.modelCore)
    testImplementation(projects.fileCollections)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.snapshots))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
