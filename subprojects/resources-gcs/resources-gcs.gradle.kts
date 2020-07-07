plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))
    implementation(project(":core"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.jacksonCore)
    implementation(libs.jacksonAnnotations)
    implementation(libs.jacksonDatabind)
    implementation(libs.gcs)
    implementation(libs.commonsHttpclient)
    implementation(libs.joda)

    testImplementation(libs.groovy)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependencyManagement")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":modelCore"))
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
}
