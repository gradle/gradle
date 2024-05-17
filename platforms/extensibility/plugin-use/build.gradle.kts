plugins {
    id("gradlebuild.distribution.api-java")
}

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 7 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core-api"))
    api(project(":core"))
    api(project(":dependency-management"))
    api(project(":file-collections"))
    api(project(":java-language-extensions"))
    api(project(":logging"))
    api(project(":messaging"))
    api(project(":model-core"))

    api(libs.guava)
    api(libs.jsr305)

    implementation(project(":functional"))

    implementation(project(":jvm-services"))
    implementation(project(":problems-api"))

    testImplementation(testFixtures(project(":resources-http")))

    integTestImplementation(project(":base-services-groovy"))
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.groovyTest)

    integTestDistributionRuntimeOnly(project(":distributions-basics")) {
        because("Requires test-kit: 'java-gradle-plugin' is used in integration tests which always adds the test-kit dependency.")
    }
}

testFilesCleanup.reportOnly = true

description = """Provides functionality for resolving and managing plugins during their application to projects."""

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
