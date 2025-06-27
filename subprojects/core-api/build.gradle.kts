plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public and internal 'core' Gradle APIs that are required by other subprojects"

errorprone {
    disabledChecks.addAll(
        "NonApiType", // 1 occurrences
        "ReferenceEquality", // 2 occurrences
        "StringCharset", // 1 occurrences
    )
}

dependencies {
    compileOnly(libs.jetbrainsAnnotations)

    api(projects.stdlibJavaExtensions)
    api(projects.buildCacheSpi)
    api(projects.loggingApi)
    api(projects.baseServices)
    api(projects.files)
    api(projects.resources)
    api(projects.persistentCache)
    api(projects.declarativeDslApi)
    api(libs.jspecify)
    api(libs.groovy)
    api(libs.groovyAnt)
    api(libs.guava)
    api(libs.ant)
    api(libs.inject)

    implementation(projects.io)
    implementation(projects.baseServicesGroovy)
    implementation(projects.logging)
    implementation(projects.buildProcessServices)

    implementation(libs.commonsLang)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    runtimeOnly(libs.kotlinReflect)

    testImplementation(libs.asm)
    testImplementation(libs.asmCommons)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    testFixturesImplementation(projects.baseServices)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

// AutoTestedSamplesCoreApiIntegrationTest includes customized test logic, so automatic auto testing samples generation is not needed (and would fail) in this project
integTest.generateDefaultAutoTestedSamplesTest = false
testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
