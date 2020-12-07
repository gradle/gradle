plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":persistent-cache"))
    implementation(project(":process-services"))
    implementation(project(":resources"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(libs.asm)
    testImplementation(libs.asmCommons)
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":base-services"))

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

classycle {
    excludePatterns.add("org/gradle/**")
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreParameterizedVarargType() // [unchecked] Possible heap pollution from parameterized vararg type: ArtifactResolutionQuery, RepositoryContentDescriptor, HasMultipleValues
}

integTest.usesSamples.set(true)
testFilesCleanup.reportOnly.set(true)
