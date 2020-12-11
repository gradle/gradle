plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:build-cache")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:logging")
    implementation("org.gradle:resources")
    implementation("org.gradle:resources-http")

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsHttpclient)
    implementation(libs.inject)

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(libs.servletApi)

    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
