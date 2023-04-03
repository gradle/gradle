plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":build-option"))

    implementation(libs.groovy)
    implementation(libs.guava)

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

