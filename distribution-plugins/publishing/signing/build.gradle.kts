plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:file-collections")

    implementation("org.gradle:plugins")
    implementation("org.gradle:dependency-management")
    implementation("org.gradle:publish")
    implementation("org.gradle:maven")
    implementation("org.gradle:security")

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation("org.gradle:ivy")
    testImplementation(testFixtures("org.gradle:core"))

    testRuntimeOnly(testFixtures("org.gradle:security"))
    testRuntimeOnly(project(":distributions-publishing")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(project(":distributions-publishing"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

classycle {
    excludePatterns.add("org/gradle/plugins/signing/**")
}

integTest.usesSamples.set(true)
