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
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":resources"))
    implementation(project(":core"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.jsch)
    implementation(libs.commonsIo)

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
