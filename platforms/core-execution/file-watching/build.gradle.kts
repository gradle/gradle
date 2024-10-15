plugins {
    id("gradlebuild.distribution.api-java")
}

description = "File system watchers for keeping the VFS up-to-date"

dependencies {
    api(projects.snapshots)
    api(projects.buildOperations)
    api(projects.files)
    api(projects.stdlibJavaExtensions)

    api(libs.gradleFileEvents)
    api(libs.jsr305)
    api(libs.nativePlatform)
    api(libs.slf4jApi)
    implementation(projects.functional)

    implementation(libs.guava)

    testImplementation(projects.processServices)
    testImplementation(projects.resources)
    testImplementation(projects.persistentCache)
    testImplementation(projects.buildOption)
    testImplementation(projects.enterpriseOperations)
    testImplementation(testFixtures(projects.buildOperations))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.fileCollections))
    testImplementation(testFixtures(projects.toolingApi))
    testImplementation(testFixtures(projects.launcher))
    testImplementation(testFixtures(projects.snapshots))

    testImplementation(libs.commonsIo)

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Uses application plugin.")
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
