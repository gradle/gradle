plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with repositories over sftp"

dependencies {
    api(projects.concurrent)
    api(projects.stdlibJavaExtensions)
    api(projects.serviceProvider)
    api(projects.coreApi)
    api(projects.resources)

    api(libs.jsch)

    implementation(projects.core)

    implementation(libs.commonsIo)
    implementation(libs.guava)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.ivy))
    testImplementation(testFixtures(projects.maven))

    integTestImplementation(projects.logging)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.sshdCore)
    integTestImplementation(libs.sshdScp)
    integTestImplementation(libs.sshdSftp)

    integTestDistributionRuntimeOnly(projects.distributionsBasics)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
