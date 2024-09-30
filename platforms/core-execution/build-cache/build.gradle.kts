plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.jmh")
}

description = "Implementation of build cache controller and factories"

dependencies {
    api(projects.buildCacheBase)
    api(projects.buildCachePackaging)
    api(projects.buildCacheSpi)
    api(projects.buildOperations)
    api(projects.enterpriseOperations)
    api(projects.files)
    api(projects.hashing)
    api(projects.snapshots)

    api(libs.jsr305)

    api(projects.stdlibJavaExtensions)
    implementation(libs.commonsIo)
    api(libs.guava)
    implementation(libs.slf4jApi)

    jmhImplementation(projects.baseServices)
    jmhImplementation(projects.native)
    jmhImplementation(platform(projects.distributionsDependencies))
    jmhImplementation(libs.aircompressor)
    jmhImplementation(libs.commonsCompress)
    jmhImplementation(libs.commonsIo)
    jmhImplementation(libs.jtar)
    jmhImplementation(libs.snappy)

    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.buildOperations))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.snapshots))

    testFixturesImplementation(testFixtures(projects.hashing))

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
