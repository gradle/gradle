plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":resources"))
    implementation(project(":base-services-groovy"))
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.ant)
    implementation(libs.ivy)
    implementation(libs.maven3)
    implementation(libs.pmavenCommon)
    implementation(libs.pmavenGroovy)
    implementation(libs.maven3WagonFile)
    implementation(libs.maven3WagonHttp)
    implementation(libs.plexusContainer)
    implementation(libs.aetherConnector)

    testImplementation(project(":native"))
    testImplementation(project(":process-services"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":resources-http"))
    testImplementation(libs.xmlunit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation(project(":ear"))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":dependency-management"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
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
