plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:files")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:file-temp")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:tooling-api")

    implementation("org.gradle:dependency-management")
    implementation("org.gradle:platform-base")
    implementation("org.gradle:plugins")
    implementation("org.gradle:publish")
    implementation("org.gradle:maven")
    implementation("org.gradle:ivy")
    implementation("org.gradle:version-control")

    implementation(project(":platform-native"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testFixturesApi("org.gradle:base-services") {
        because("Test fixtures export the Named class")
    }
    testFixturesApi("org.gradle:platform-base") {
        because("Test fixtures export the Platform class")
    }

    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(testFixtures(project(":platform-native")))

    testImplementation("org.gradle:native")
    testImplementation("org.gradle:resources")
    testImplementation("org.gradle:base-services-groovy")
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:messaging"))
    testImplementation(testFixtures("org.gradle:snapshots"))
    testImplementation(testFixtures("org.gradle:version-control"))
    testImplementation(testFixtures("org.gradle:platform-base"))
    testImplementation(testFixtures(project(":platform-native")))

    integTestImplementation("org.gradle:native")
    integTestImplementation("org.gradle:resources")
    integTestImplementation(libs.nativePlatform)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.jgit)

    testRuntimeOnly("org.gradle:distributions-core") {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

classycle {
    excludePatterns.add("org/gradle/language/nativeplatform/internal/**")
}

integTest.usesSamples.set(true)
