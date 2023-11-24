plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with HTTP build caches"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":build-cache-spi"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(project(":problems-api"))
    implementation(project(":resources"))
    implementation(project(":resources-http"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsHttpclient)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.servletApi)

    integTestImplementation(project(":enterprise-operations"))
    integTestImplementation(testFixtures(project(":build-cache")))
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
