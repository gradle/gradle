plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation for interacting with repositories over sftp"

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 2 occurrences
    )
}

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":core-api"))
    api(project(":resources"))

    api(libs.jsch)

    implementation(project(":core"))

    implementation(libs.commonsIo)
    implementation(libs.guava)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":ivy")))
    testImplementation(testFixtures(project(":maven")))

    integTestImplementation(project(":logging"))
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.sshdCore)
    integTestImplementation(libs.sshdScp)
    integTestImplementation(libs.sshdSftp)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
}
