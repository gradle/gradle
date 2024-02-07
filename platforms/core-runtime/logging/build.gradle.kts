plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging infrastructure"

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "AnnotateFormatMethod", // 1 occurrences
        "DefaultCharset", // 3 occurrences
        "DoubleBraceInitialization", // 1 occurrences
        "FutureReturnValueIgnored", // 2 occurrences
        "InlineFormatString", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "NullableVoid", // 1 occurrences
        "OverridingMethodInconsistentArgumentNamesChecker", // 15 occurrences
        "ReferenceEquality", // 1 occurrences
        "SameNameButDifferent", // 11 occurrences
        "StringCaseLocaleUsage", // 12 occurrences
        "StringSplitter", // 4 occurrences
        "ThreadLocalUsage", // 1 occurrences
        "TypeParameterUnusedInFormals", // 1 occurrences
        "UnnecessaryParentheses", // 3 occurrences
        "UnusedMethod", // 3 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":build-option"))
    api(project(":cli"))
    api(project(":enterprise-logging"))
    api(project(":enterprise-workers"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":native"))
    api(project(":problems-api"))

    api(libs.jansi)
    api(libs.jsr305)
    api(libs.slf4jApi)


    implementation(libs.julToSlf4j)
    implementation(libs.ant)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.guava)

    // GSon is not strictly required here but removing it moves the dependency in the distribution from lib to lib/plugins
    // TODO Check if this is an issue
    runtimeOnly(libs.gson)
    runtimeOnly(libs.jclToSlf4j)
    runtimeOnly(libs.log4jToSlf4j)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":testing-jvm")))
    testImplementation(libs.groovyDatetime)
    testImplementation(libs.groovyDateUtil)

    integTestImplementation(libs.ansiControlSequenceUtil)

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":enterprise-workers"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/internal/featurelifecycle/**")
    excludePatterns.add("org/gradle/util/**")
}
