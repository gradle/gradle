plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":resources"))
    implementation(project(":resourcesHttp"))
    implementation(project(":core"))

    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.commons_lang)
    implementation(libs.jackson_core)
    implementation(libs.jackson_annotations)
    implementation(libs.jackson_databind)
    implementation(libs.gcs)
    implementation(libs.commons_httpclient)
    implementation(libs.joda)

    testImplementation(libs.groovy)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependencyManagement")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":coreApi"))
    integTestImplementation(project(":modelCore"))
    integTestImplementation(libs.commons_io)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly(project(":distributionsBasics"))
}
