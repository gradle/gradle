plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Implementation of build cache controller and factories"

dependencies {
    api(project(":build-cache-base"))
    api(project(":snapshots"))

    implementation(project(":build-cache-packaging"))
    implementation(project(":base-services"))
    implementation(project(":enterprise-operations"))
    implementation(project(":files"))
    implementation(project(":file-temp"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.commonsIo)

    jmhImplementation(platform(project(":distributions-dependencies")))
    jmhImplementation(libs.ant)
    jmhImplementation(libs.commonsCompress)
    jmhImplementation(libs.aircompressor)
    jmhImplementation(libs.snappy)
    jmhImplementation(libs.jtar)
    jmhImplementation(libs.commonsIo)

    testImplementation(project(":model-core"))
    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":snapshots")))

    testFixturesImplementation(testFixtures(project(":hashing")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
