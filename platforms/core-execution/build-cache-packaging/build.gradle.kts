plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Package build cache results"

dependencies {
    api(project(":build-cache-base"))
    api(project(":snapshots"))
    api(project(":hashing"))
    api(project(":files"))

    implementation(project(":base-annotations"))
    implementation(project(":wrapper-shared")) {
        because("We need to access the ZipSlip helper class")
    }
    implementation(libs.guava)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)

    testImplementation(project(":process-services"))
    testImplementation(project(":file-collections"))
    testImplementation(project(":resources"))

    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":core-api")))
}
