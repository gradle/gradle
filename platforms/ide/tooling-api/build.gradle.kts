plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.shaded-jar")
}

description = "Gradle Tooling API - the programmatic API to invoke Gradle"

gradlebuildJava.usedInToolingApi()

tasks.named<Jar>("sourcesJar") {
    // duplicate package-info.java because of split packages
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

shadedJar {
    shadedConfiguration.exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
    keepPackages = listOf("org.gradle.tooling")
    unshadedPackages = listOf("org.gradle", "org.slf4j", "sun.misc")
    ignoredPackages = setOf("org.gradle.tooling.provider.model")
}

errorprone {
    disabledChecks.addAll(
        "EqualsUnsafeCast", // 1 occurrences
        "FutureReturnValueIgnored", // 1 occurrences
        "LockNotBeforeTry", // 1 occurrences
        "ThreadLocalUsage", // 2 occurrences
    )
}

dependencies {
    shadedImplementation(libs.slf4jApi)

    runtimeOnly(projects.coreApi)
    implementation(projects.core)
    implementation(projects.buildProcessServices)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.guava)

    api(libs.jsr305)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.concurrent)
    api(projects.enterpriseLogging)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.messaging)
    api(projects.time)
    api(projects.wrapperShared)

    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.modelCore)
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.baseServicesGroovy)
    testFixturesImplementation(projects.internalTesting)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.slf4jApi)

    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.persistentCache)

    crossVersionTestImplementation(projects.jvmServices)
    crossVersionTestImplementation(testFixtures(projects.problemsApi))
    crossVersionTestImplementation(libs.jettyWebApp)
    crossVersionTestImplementation(libs.commonsIo)
    crossVersionTestRuntimeOnly(libs.cglib) {
        because("BuildFinishedCrossVersionSpec classpath inference requires cglib enhancer")
    }

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.ide))
    testImplementation(testFixtures(projects.workers))

    integTestNormalizedDistribution(projects.distributionsFull) {
        because("Used by ToolingApiRemoteIntegrationTest")
    }

    integTestDistributionRuntimeOnly(projects.distributionsFull)
    integTestLocalRepository(project(path)) {
        because("ToolingApiResolveIntegrationTest and ToolingApiClasspathIntegrationTest use the Tooling API Jar")
    }

    crossVersionTestDistributionRuntimeOnly(projects.distributionsFull)
    crossVersionTestLocalRepository(project(path)) {
        because("ToolingApiVersionSpecification uses the Tooling API Jar")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/tooling/**")
}

tasks.named("toolingApiShadedJar") {
    // TODO: Remove this workaround once issue is fixed for configuration cache
    // We don't add tasks that complete at configuration time
    // to the resulting work graph, and then prune projects that have no tasks in the graph.
    // This happens to java-api-extractor, since it's built with rest of build-logic.
    // Could be related to https://github.com/gradle/gradle/issues/24273
    dependsOn(gradle.includedBuild("build-logic").task(":java-api-extractor:assemble"))
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true

apply(from = "buildship.gradle")
tasks.isolatedProjectsIntegTest {
    enabled = false
}
