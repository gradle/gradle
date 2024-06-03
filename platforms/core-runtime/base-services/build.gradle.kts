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

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(projects.fileTemp)
    api(projects.serviceProvider)
    api(project(":hashing"))
    api(project(":build-operations"))
    api(libs.inject)
    api(libs.jsr305)
    api(libs.guava)

    implementation(projects.io)
    implementation(projects.time)

    implementation(libs.asm)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
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
