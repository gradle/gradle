plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public and internal 'core' Gradle APIs that are required by other subprojects"

dependencies {
    api(project(":process-services"))

    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":enterprise-operations"))
    implementation(project(":files"))
    implementation(project(":logging"))
    implementation(project(":persistent-cache"))
    implementation(project(":resources"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(libs.asm)
    testImplementation(libs.asmCommons)
    testImplementation(testFixtures(project(":logging")))
    testImplementation(project(":problems"))
    testImplementation(project(":build-operations"))

    testFixturesImplementation(project(":base-services"))

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreParameterizedVarargType() // [unchecked] Possible heap pollution from parameterized vararg type: ArtifactResolutionQuery, RepositoryContentDescriptor, HasMultipleValues
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
