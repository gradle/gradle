plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:files")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:execution")
    implementation(project(":dependency-management"))
    implementation(project(":workers"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:core-api"))
    testImplementation("org.gradle:native")
    testImplementation("org.gradle:snapshots")
    testImplementation("org.gradle:process-services")

    testFixturesApi("org.gradle:core")
    testFixturesApi("org.gradle:file-collections")
    testFixturesApi(testFixtures("org.gradle:model-core"))
    testFixturesImplementation(libs.guava)
    testFixturesApi(testFixtures("org.gradle:model-core"))
    testFixturesApi(testFixtures(project(":diagnostics")))

    testRuntimeOnly(project(":distributions-core")) {
        because("RuntimeShadedJarCreatorTest requires a distribution to access the ...-relocated.txt metadata")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.add("org/gradle/**")
}

integTest.usesSamples.set(true)
