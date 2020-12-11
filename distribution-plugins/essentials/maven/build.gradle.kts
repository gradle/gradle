plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:resources")
    implementation("org.gradle:base-services-groovy")
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.maven3Model) {
        because("We use the metadata model classes to create POM metadata files for components")
    }
    implementation(libs.maven3RepositoryMetadata) {
        because("We use the metadata model classes to create repository metadata files")
    }

    testImplementation("org.gradle:native")
    testImplementation("org.gradle:process-services")
    testImplementation("org.gradle:snapshots")
    testImplementation("org.gradle:resources-http")
    testImplementation(libs.xmlunit)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:model-core"))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation("org.gradle:ear")

    testFixturesApi("org.gradle:base-services") {
        because("Test fixtures export the Action class")
    }
    testFixturesImplementation("org.gradle:core-api")
    testFixturesImplementation(project(":dependency-management"))
    testFixturesImplementation("org.gradle:internal-integ-testing")

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-jvm")
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreDeprecations() // old 'maven' publishing mechanism: types are deprecated
    ignoreRawTypes() // old 'maven' publishing mechanism: raw types used in public API
}

classycle {
    excludePatterns.add("org/gradle/api/publication/maven/internal/**")
    excludePatterns.add("org/gradle/api/artifacts/maven/**")
}

integTest.usesSamples.set(true)
