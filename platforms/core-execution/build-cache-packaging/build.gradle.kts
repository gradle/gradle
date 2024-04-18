plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Package build cache results"

dependencies {

    api(project(":build-cache-base"))
    api(project(":files"))
    api(project(":hashing"))
    api(project(":snapshots"))

    api(libs.guava)

    implementation(projects.javaLanguageExtensions)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.jsr305)

    testImplementation(project(":file-collections"))
    testImplementation(project(":process-services"))
    testImplementation(project(":resources"))

    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":snapshots")))
}
