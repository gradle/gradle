plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api("org.gradle:resources")
    implementation("org.gradle:base-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:model-core")
    implementation("org.gradle:logging")

    implementation(libs.commonsHttpclient)
    implementation(libs.slf4jApi)
    implementation(libs.jclToSlf4j)
    implementation(libs.jcifs)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.xerces)
    implementation(libs.nekohtml)

    testImplementation("org.gradle:internal-integ-testing")
    testImplementation(libs.jettyWebApp)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:logging"))

    testFixturesImplementation("org.gradle:base-services")
    testFixturesImplementation("org.gradle:logging")
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
