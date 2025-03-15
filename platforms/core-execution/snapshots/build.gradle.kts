plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to take immutable, comparable snapshots of files and other things"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.files)
    api(projects.functional)
    api(projects.hashing)

    api(libs.guava)
    api(libs.jsr305)

    implementation(libs.slf4jApi)

    testImplementation(projects.processServices)
    testImplementation(projects.resources)
    testImplementation(projects.native)
    testImplementation(projects.persistentCache)
    testImplementation(libs.ant)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.fileCollections))
    testImplementation(testFixtures(projects.messaging))

    testFixturesApi(testFixtures(projects.hashing))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.fileCollections)
    testFixturesImplementation(libs.commonsIo)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
