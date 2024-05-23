plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains project diagnostics or report tasks, e.g. help, project report, dependency report and similar"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "InlineMeInliner", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "NonApiType", // 5 occurrences
        "ProtectedMembersInFinalClass", // 1 occurrences
        "StringCaseLocaleUsage", // 3 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":enterprise-logging"))
    api(project(":file-collections"))
    api(project(":logging"))
    api(project(":model-core"))
    api(project(":platform-base"))
    api(project(":reporting"))

    api(libs.groovy)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.concurrent)
    implementation(project(":functional"))
    implementation(project(":logging-api"))

    implementation(libs.groovyJson)
    implementation(libs.commonsLang)
    implementation(libs.jatl)

    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)
    integTestImplementation(testFixtures(project(":declarative-dsl-provider")))

    testFixturesApi(testFixtures(project(":platform-native")))
    testFixturesApi(testFixtures(project(":logging")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))  {
        because("There are integration tests that assert that all the tasks of a full distribution are reported (these should probably move to ':integTests').")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/api/reporting/model/internal/*")
    excludePatterns.add("org/gradle/api/reporting/dependencies/internal/*")
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}
