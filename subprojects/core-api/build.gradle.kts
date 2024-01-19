plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public and internal 'core' Gradle APIs that are required by other subprojects"

dependencies {
    compileOnly(libs.jetbrainsAnnotations)

    api(project(":process-services"))
    api(project(":base-annotations"))
    api(project(":logging-api"))
    api(project(":base-services"))
    api(project(":files"))
    api(project(":resources"))
    api(project(":persistent-cache"))

    api(libs.jsr305)
    api(libs.groovy)
    api(libs.groovyAnt)
    api(libs.guava)
    api(libs.ant)
    api(libs.inject)

    compileOnly(libs.jetbrainsAnnotations)

    implementation(project(":base-services-groovy"))
    implementation(project(":logging"))

    api(libs.restrictedKotlin)
    runtimeOnly(libs.futureKotlin("reflect"))
    implementation(libs.commonsLang)
    implementation(libs.slf4jApi)

    testImplementation(libs.asm)
    testImplementation(libs.asmCommons)
    testImplementation(testFixtures(project(":logging")))

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
