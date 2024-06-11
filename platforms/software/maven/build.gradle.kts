plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of the Maven Publish Plugin that provides the ability to publish build artifacts to Maven repositories."

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "EqualsUnsafeCast", // 1 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
        "UnusedMethod", // 4 occurrences
        "UnusedVariable", // 3 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-collections"))
    api(project(":logging"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":publish"))
    api(project(":resources"))

    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.maven3Model) {
        because("We use the metadata model classes to create POM metadata files for components")
    }
    api(libs.maven3RepositoryMetadata) {
        because("We use the metadata model classes to create repository metadata files")
    }

    implementation(project(":functional"))
    implementation(project(":hashing"))
    implementation(project(":logging-api"))

    implementation(libs.commonsLang)
    implementation(libs.plexusUtils)
    implementation(libs.slf4jApi)

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
