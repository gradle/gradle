plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:resources")
    implementation("org.gradle:core")

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.jsch)
    implementation(libs.commonsIo)

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:dependency-management"))
    testImplementation(testFixtures("org.gradle:ivy"))
    testImplementation(testFixtures("org.gradle:maven"))

    integTestImplementation("org.gradle:logging")
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.sshdCore)
    integTestImplementation(libs.sshdScp)
    integTestImplementation(libs.sshdSftp)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
