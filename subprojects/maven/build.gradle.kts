plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of the Maven Publish Plugin that provides the ability to publish build artifacts to Maven repositories."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":functional"))
    implementation(project(":dependency-management"))
    implementation(project(":file-collections"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))
    implementation(project(":resources"))

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

    testImplementation(project(":native"))
    testImplementation(project(":process-services"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources-http"))
    testImplementation(libs.xmlunit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation(project(":enterprise-operations"))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":dependency-management"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreDeprecations() // old 'maven' publishing mechanism: types are deprecated
    ignoreRawTypes() // old 'maven' publishing mechanism: raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/api/publication/maven/internal/**")
    excludePatterns.add("org/gradle/api/artifacts/maven/**")
}

integTest.usesJavadocCodeSnippets = true
