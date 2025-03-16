plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Package build cache results"

dependencies {

    api(projects.buildCacheBase)
    api(projects.files)
    api(projects.hashing)
    api(projects.snapshots)
    api(projects.stdlibJavaExtensions)

    api(libs.guava)

    implementation(projects.wrapperShared)

    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.jspecify)

    testImplementation(projects.fileCollections)
    testImplementation(projects.processServices)
    testImplementation(projects.resources)

    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.coreApi))
    testImplementation(testFixtures(projects.snapshots))
}
