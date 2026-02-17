plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
    id("gradlebuild.shaded-jar")
}

description = "Gradle Tooling API - the programmatic API to invoke Gradle"

gradleModule {
    targetRuntimes {
        usedInClient = true
    }
}

jvmCompile {
    compilations {
        named("main") {
            // JSpecify annotations on static inner type return types
            usesJdkInternals = true
        }
        named("testFixtures") {
            // The cross version tests depend on the test fixtures
            targetJvmVersion = 8
        }
        named("crossVersionTest") {
            // The TAPI tests must be able to run the TAPI client, which is still JVM 8 compatible
            targetJvmVersion = 8
        }
    }
}

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

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.classloaders)
    api(projects.concurrent)
    api(projects.enterpriseLogging)
    api(projects.messaging)
    api(projects.stdlibJavaExtensions)
    api(projects.time)
    api(projects.wrapperShared)

    api(libs.jspecify)

    implementation(projects.buildDiscoveryImpl)
    implementation(projects.core)
    implementation(projects.functional)
    implementation(projects.logging)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.guava)
    implementation(libs.jsr305)

    shadedImplementation(libs.slf4jApi)

    runtimeOnly(projects.coreApi)

    testImplementation(projects.internalDistributionTesting)

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.baseServicesGroovy)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.internalDistributionTesting)
    testFixturesImplementation(projects.internalTesting)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.modelCore)
    testFixturesImplementation(testFixtures(projects.buildProcessServices))
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.slf4jApi)

    testFixturesRuntimeOnly(testLibs.spockJUnit4) {
        because("Required for @org.junit.Rule, used in ToolingApiSpecification")
    }

    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.persistentCache)
    integTestImplementation(projects.kotlinDslToolingModels)
    integTestImplementation(testFixtures(projects.buildProcessServices))
    integTestImplementation(testFixtures(projects.launcher))

    crossVersionTestImplementation(projects.jvmServices)
    crossVersionTestImplementation(projects.internalTesting)
    crossVersionTestImplementation(testFixtures(projects.buildProcessServices))
    crossVersionTestImplementation(testFixtures(projects.problemsApi))
    crossVersionTestImplementation(libs.commonsIo)
    crossVersionTestImplementation(testLibs.jettyWebApp)
    crossVersionTestRuntimeOnly(testLibs.cglib) {
        because("BuildFinishedCrossVersionSpec classpath inference requires cglib enhancer")
    }

    testImplementation(projects.buildEvents)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.time))

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

testFilesCleanup.reportOnly = true

apply(from = "buildship.gradle")
tasks.isolatedProjectsIntegTest {
    enabled = false
}

// AutoTestedSamplesToolingApiTest includes customized test logic, so automatic auto testing samples generation is not needed (and would fail) in this project
integTest.generateDefaultAutoTestedSamplesTest = false
