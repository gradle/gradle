plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":baseServices"))

    implementation(libs.fastutil)
    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.kryo)

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(libs.slf4j_api)

    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}
