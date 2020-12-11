plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:resources")
    implementation("org.gradle:core")

    implementation("org.gradle:resources-http")

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonDatabind)
    implementation(libs.gcs)

    testImplementation(libs.groovy)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:dependency-management"))
    testImplementation(testFixtures("org.gradle:ivy"))
    testImplementation(testFixtures("org.gradle:maven"))

    integTestImplementation("org.gradle:core-api")
    integTestImplementation("org.gradle:model-core")
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.joda)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
