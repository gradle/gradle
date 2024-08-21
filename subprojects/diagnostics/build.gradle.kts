plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Contains project diagnostics or report tasks, e.g. help, project report, dependency report and similar"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "InlineMeInliner", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "NonApiType", // 5 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
    )
}

dependencies {
    api(projects.jvmServices)
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.enterpriseLogging)
    api(projects.fileCollections)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.reportRendering)
    api(projects.reporting)

    api(libs.groovy)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.concurrent)
    implementation(projects.functional)
    implementation(projects.loggingApi)

    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.jatl)

    testImplementation(projects.processServices)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.logging))

    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)
    integTestImplementation(testFixtures(projects.declarativeDslProvider))

    testFixturesApi(testFixtures(projects.platformNative))
    testFixturesApi(testFixtures(projects.logging))
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(libs.guava)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsFull)  {
        because("There are integration tests that assert that all the tasks of a full distribution are reported (these should probably move to ':integTests').")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/api/reporting/model/internal/*")
    excludePatterns.add("org/gradle/api/reporting/dependencies/internal/*")
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}
