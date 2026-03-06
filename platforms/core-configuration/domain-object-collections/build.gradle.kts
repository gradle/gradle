plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Domain object collection infrastructure for Gradle's container types"

strictCompile {
    ignoreRawTypes() // raw types in Groovy Closure-based API overrides
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/**")
}

dependencies {
    api(projects.coreApi)
    api(projects.declarativeDslApi)
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.modelCore)
    api(projects.serviceLookup)

    api(libs.groovy)
    api(libs.guava)

    implementation(projects.baseServicesGroovy)
    implementation(projects.functional)
    implementation(projects.logging)

    implementation(libs.commonsLang)
    implementation(libs.fastutil)

    compileOnly(libs.jspecify)

    testFixturesApi(projects.internalIntegTesting)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(testFixtures(projects.core))

    testImplementation(projects.core)
    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests use TestUtil which requires runtime service infrastructure")
    }

    integTestDistributionRuntimeOnly(projects.distributionsCore) {
        because("Integration tests run full Gradle builds requiring runtime service infrastructure")
    }
}
