plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to take immutable, comparable snapshots of files and other things"

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":files"))
    api(project(":functional"))
    api(project(":hashing"))

    api(libs.guava)
    api(libs.jsr305)

    implementation(libs.slf4jApi)

    testImplementation(project(":process-services"))
    testImplementation(project(":resources"))
    testImplementation(project(":native"))
    testImplementation(project(":persistent-cache"))
    testImplementation(libs.ant)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":file-collections")))
    testImplementation(testFixtures(project(":messaging")))

    testFixturesApi(testFixtures(project(":hashing")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(libs.commonsIo)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
