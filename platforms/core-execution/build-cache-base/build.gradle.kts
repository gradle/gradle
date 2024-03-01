plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared build cache classes"

dependencies {
    api(projects.buildCacheSpi)

    implementation(projects.baseAnnotations)
    implementation(projects.hashing)
    implementation(projects.files)

    testImplementation(testFixtures(projects.hashing))
}
