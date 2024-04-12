plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public and internal 'core' Gradle APIs that are required by other subprojects"

errorprone {
    disabledChecks.addAll(
        "BadImport", // 1 occurrences
        "EmptyBlockTag", // 5 occurrences
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

    api(project(":process-services"))
    api(projects.javaLanguageExtensions)
    api(project(":build-cache-spi"))
    api(project(":logging-api"))
    api(project(":base-services"))
    api(project(":files"))
    api(project(":resources"))
    api(project(":persistent-cache"))
    api(project(":declarative-dsl-api"))
    api(libs.jsr305)
    api(libs.groovy)
    api(libs.groovyAnt)
    api(libs.guava)
    api(libs.ant)
    api(libs.inject)

    implementation(project(":base-services-groovy"))
    implementation(project(":logging"))
    implementation(libs.commonsLang)
    implementation(libs.slf4jApi)

    runtimeOnly(libs.futureKotlin("reflect"))

    testImplementation(libs.asm)
    testImplementation(libs.asmCommons)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":base-services"))

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
