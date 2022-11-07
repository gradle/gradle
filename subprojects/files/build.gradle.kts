plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Base tools to work with files"

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":functional"))
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(project(":native"))
    testImplementation(project(":base-services")) {
        because("TextUtil is needed")
    }
    testImplementation(testFixtures(project(":native")))
}
