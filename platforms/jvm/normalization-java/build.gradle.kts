plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "API extraction for Java"

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":hashing"))
    implementation(project(":files"))
    implementation(project(":snapshots"))
    implementation(project(":functional"))

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)

    testImplementation(project(":base-services"))
    testImplementation(project(":internal-testing"))
    testImplementation(testFixtures(project(":snapshots")))
}
