plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")

    implementation(project(":dependency-management"))
    implementation(project(":plugin-use"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(project(":file-watching"))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation("org.gradle:build-option")
    integTestImplementation("org.gradle:launcher")

    integTestDistributionRuntimeOnly("org.gradle:distributions-basics") {
        because("Requires test-kit: 'java-gradle-plugin' is used in some integration tests which always adds the test-kit dependency.")
    }
}

testFilesCleanup.reportOnly.set(true)
