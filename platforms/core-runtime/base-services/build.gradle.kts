plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "A set of generic services and utilities."

gradlebuildJava.usedInWorkers()

/**
 * Use Java 8 compatibility for Unit tests, so we can test Java 8 features as well
 */
tasks.named<JavaCompile>("compileTestJava") {
    options.release = 8
}

/**
 * Use Java 8 compatibility for JMH benchmarks
 */
tasks.named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.release = 8
}

moduleIdentity.createBuildReceipt()

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 4 occurrences
        "EmptyBlockTag", // 2 occurrences
        "EscapedEntity", // 1 occurrences
        "FutureReturnValueIgnored", // 1 occurrences
        "ImmutableEnumChecker", // 1 occurrences
        "InlineFormatString", // 2 occurrences
        "InlineMeSuggester", // 1 occurrences
        "JavaLangClash", // 1 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "MixedMutabilityReturnType", // 3 occurrences
        "NonAtomicVolatileUpdate", // 2 occurrences
        "ReturnValueIgnored", // 1 occurrences
        "StringCaseLocaleUsage", // 8 occurrences
        "StringSplitter", // 3 occurrences
        "ThreadLocalUsage", // 4 occurrences
        "TypeParameterUnusedInFormals", // 5 occurrences
        "URLEqualsHashCode", // 1 occurrences
        "UnnecessaryParentheses", // 2 occurrences
        "UnsynchronizedOverridesSynchronized", // 2 occurrences
        "UnusedMethod", // 2 occurrences
        "UnusedVariable", // 3 occurrences
    )
}

dependencies {
    api(project(":base-annotations"))
    api(project(":hashing"))
    api(project(":build-operations"))
    api(libs.jsr305)
    api(libs.guava)

    implementation(libs.asm)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.slf4jApi)

    integTestImplementation(project(":logging"))

    testFixturesApi(project(":hashing"))
    testFixturesImplementation(libs.guava)
    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.xerces)

    integTestDistributionRuntimeOnly(project(":distributions-core"))

    jmh(platform(project(":distributions-dependencies")))
    jmh(libs.bouncycastleProvider)
    jmh(libs.guava)
}

packageCycles {
    // Needed for the factory methods in the base class
    excludePatterns.add("org/gradle/util/GradleVersion**")
}

jmh.includes = listOf("HashingAlgorithmsBenchmark")
