plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and domain objects for testing native code"

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":diagnostics"))
    api(project(":java-language-extensions"))
    api(project(":language-native"))
    api(project(":model-core"))
    api(project(":native"))
    api(project(":platform-base"))
    api(project(":platform-native"))
    api(project(":process-services"))
    api(project(":test-suites-base"))
    api(project(":testing-base"))
    api(project(":testing-base-infrastructure"))
    api(project(":time"))

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.io)
    implementation(project(":logging"))
    implementation(project(":logging-api"))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)

    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":testing-base")))
    testImplementation(testFixtures(project(":language-native")))
    testImplementation(testFixtures(project(":ide")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
