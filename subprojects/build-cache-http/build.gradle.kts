plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with HTTP build caches"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":build-cache"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsHttpclient)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.servletApi)

    integTestImplementation(project(":enterprise-operations"))
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
