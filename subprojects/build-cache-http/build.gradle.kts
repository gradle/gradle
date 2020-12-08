plugins {
    id("gradlebuild.distribution.api-java")
}

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
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.servletApi)

    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
