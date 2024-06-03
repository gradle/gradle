plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with Google Cloud Storage (GCS) repositories"

errorprone {
    disabledChecks.addAll(
        "StringCaseLocaleUsage", // 1 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":resources"))

    api(libs.gcs)
    api(libs.jsr305)

    implementation(projects.javaLanguageExtensions)
    implementation(project(":hashing"))
    implementation(project(":logging-api"))

    implementation(libs.commonsLang)
    implementation(libs.googleApiClient)
    implementation(libs.googleHttpClientGson)
    implementation(libs.googleHttpClient)
    implementation(libs.googleOauthClient)
    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    testImplementation(libs.groovy)

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
