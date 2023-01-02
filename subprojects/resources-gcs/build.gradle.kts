plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with Google Cloud Storage (GCS) repositories"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))
    implementation(project(":core"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(libs.gcs)

    testImplementation(libs.groovy)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":model-core"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.joda)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}

strictCompile {
    ignoreDeprecations()
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
