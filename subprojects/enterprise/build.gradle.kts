plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Services and utilities needed by Gradle Enterprise plugin"

dependencies {
    api(project(":base-services"))
    api(project(":enterprise-operations"))
    api(project(":enterprise-logging"))

    implementation(libs.inject)
    implementation(libs.jsr305)
    implementation(libs.guava)
    implementation(project(":build-option"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":execution"))
    implementation(project(":configuration-cache"))
    implementation(project(":file-collections"))
    implementation(project(":jvm-services"))
    implementation(project(":launcher"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":process-services"))
    implementation(project(":reporting"))
    implementation(project(":snapshots"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))

    compileOnly(libs.groovy) {
        because("some used APIs (e.g. FileTree.visit) provide methods taking Groovy closures which causes compile errors")
    }

    testImplementation(project(":resources"))

    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":internal-integ-testing"))
    integTestImplementation(testFixtures(project(":core")))

    // Dependencies of the integ test fixtures
    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":messaging"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(project(":native"))
    integTestImplementation(libs.guava)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
