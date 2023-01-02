plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Included build controller and composite build infrastructure"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":enterprise-operations"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":plugin-use"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":file-watching"))
    testImplementation(project(":build-option"))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":launcher"))

    integTestDistributionRuntimeOnly(project(":distributions-basics")) {
        because("Requires test-kit: 'java-gradle-plugin' is used in some integration tests which always adds the test-kit dependency.")
    }
}

testFilesCleanup.reportOnly.set(true)

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
