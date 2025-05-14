plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with repositories over HTTP"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.coreApi)
    api(projects.core)
    api(projects.logging)
    api(projects.resources)

    api(libs.commonsHttpclient)
    api(libs.httpcore)
    api(libs.jspecify)

    implementation(projects.baseServices)
    implementation(projects.hashing)
    implementation(projects.loggingApi)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.jcifs)
    implementation(libs.jsoup)
    implementation(libs.slf4jApi)

    testImplementation(projects.internalIntegTesting)
    testImplementation(libs.jettyWebApp)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
