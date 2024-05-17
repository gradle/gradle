plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with repositories over HTTP"

errorprone {
    disabledChecks.addAll(
        "StringCaseLocaleUsage", // 2 occurrences
        "UnusedMethod", // 4 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":core-api"))
    api(project(":core"))
    api(project(":logging"))
    api(project(":resources"))

    api(libs.commonsHttpclient)
    api(libs.httpcore)
    api(libs.jsr305)

    implementation(project(":base-services"))
    implementation(project(":hashing"))
    implementation(project(":logging-api"))
    implementation(project(":model-core"))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.jcifs)
    implementation(libs.jsoup)
    implementation(libs.slf4jApi)

    testImplementation(project(":internal-integ-testing"))
    testImplementation(libs.jettyWebApp)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
