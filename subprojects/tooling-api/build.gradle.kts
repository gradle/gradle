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

dependencies {
    shadedImplementation(libs.slf4jApi)

    implementation(project(":base-services"))
    implementation(project(":enterprise-operations"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":wrapper-shared"))
    implementation(project(":persistent-cache"))

    implementation(libs.guava)

    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":base-services-groovy"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.slf4jApi)

    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":persistent-cache"))

    crossVersionTestImplementation(project(":jvm-services"))
    crossVersionTestImplementation(libs.jettyWebApp)
    crossVersionTestImplementation(libs.commonsIo)
    crossVersionTestRuntimeOnly(libs.cglib) {
        because("BuildFinishedCrossVersionSpec classpath inference requires cglib enhancer")
    }

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":ide")))
    testImplementation(testFixtures(project(":workers")))

    integTestNormalizedDistribution(project(":distributions-full")) {
        because("Used by ToolingApiRemoteIntegrationTest")
    }

    integTestDistributionRuntimeOnly(project(":distributions-full"))
    integTestLocalRepository(project(path)) {
        because("ToolingApiResolveIntegrationTest and ToolingApiClasspathIntegrationTest use the Tooling API Jar")
    }

    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
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

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true

apply(from = "buildship.gradle")
