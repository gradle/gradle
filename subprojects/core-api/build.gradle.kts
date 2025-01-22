plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Public and internal 'core' Gradle APIs that are required by other subprojects"

errorprone {
    disabledChecks.addAll(
        "InlineMeSuggester", // 1 occurrences
        "MalformedInlineTag", // 3 occurrences
        "MixedMutabilityReturnType", // 3 occurrences
        "NonApiType", // 1 occurrences
        "ObjectEqualsForPrimitives", // 2 occurrences
        "ReferenceEquality", // 2 occurrences
        "StringCharset", // 1 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    compileOnly(libs.jetbrainsAnnotations)

    api(projects.baseServices)
    api(projects.buildCacheSpi)
    api(projects.declarativeDslApi)
    api(projects.dependencyManagementBase)
    api(projects.files)
    api(projects.loggingApi)
    api(projects.persistentCache)
    api(projects.resources)
    api(projects.stdlibJavaExtensions)

    api(libs.ant)
    api(libs.groovy)
    api(libs.groovyAnt)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.io)
    implementation(projects.baseServicesGroovy)
    implementation(projects.logging)
    implementation(projects.buildProcessServices)
    implementation(libs.commonsLang)
    implementation(libs.guava)
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

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
