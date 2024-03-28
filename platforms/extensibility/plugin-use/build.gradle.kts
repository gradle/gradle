plugins {
    id("gradlebuild.distribution.api-java")
}

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 7 occurrences
    )
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":core-jvm"))
    implementation(project(":dependency-management"))
    implementation(project(":build-option"))
    implementation(project(":problems-api"))
    implementation(project(":functional"))

    implementation(libs.groovy)
    implementation(libs.guava)

    testImplementation(testFixtures(project(":resources-http")))

    integTestImplementation(project(":base-services-groovy"))
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.groovyTest)

    integTestDistributionRuntimeOnly(project(":distributions-basics")) {
        because("Requires test-kit: 'java-gradle-plugin' is used in integration tests which always adds the test-kit dependency.")
    }

    crossVersionTestImplementation(project(":base-services"))
    crossVersionTestImplementation(project(":core"))
    crossVersionTestImplementation(project(":plugins"))
    crossVersionTestImplementation(project(":platform-jvm"))
    crossVersionTestImplementation(project(":language-jvm"))
    crossVersionTestImplementation(project(":language-java"))
    crossVersionTestImplementation(project(":language-groovy"))
    crossVersionTestImplementation(project(":logging"))
    crossVersionTestImplementation(project(":scala"))
    crossVersionTestImplementation(project(":ear"))
    crossVersionTestImplementation(project(":war"))
    crossVersionTestImplementation(project(":testing-jvm"))
    crossVersionTestImplementation(project(":ide"))
    crossVersionTestImplementation(project(":ide-plugins"))
    crossVersionTestImplementation(project(":code-quality"))
    crossVersionTestImplementation(project(":signing"))
    crossVersionTestImplementation(project(":functional"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
}

testFilesCleanup.reportOnly = true

description = """Provides functionality for resolving and managing plugins during their application to projects."""

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
