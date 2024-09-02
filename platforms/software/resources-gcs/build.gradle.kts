plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with Google Cloud Storage (GCS) repositories"


dependencies {
    api(projects.serviceProvider)
    api(projects.resources)

    api(libs.gcs)
    api(libs.jsr305)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.hashing)
    implementation(projects.loggingApi)

    implementation(libs.commonsLang)
    implementation(libs.googleApiClient)
    implementation(libs.googleHttpClientGson)
    implementation(libs.googleHttpClient)
    implementation(libs.googleOauthClient)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.ivy))
    testImplementation(testFixtures(projects.maven))

    testImplementation(libs.groovy)

    integTestImplementation(projects.coreApi)
    integTestImplementation(projects.modelCore)

    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.joda)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}

strictCompile {
    ignoreDeprecations()
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
