plugins {
    id("gradlebuild.distribution.implementation-java")
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":processServices"))

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
}
