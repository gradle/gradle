plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Publishing plugin for Ivy repositories"

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 2 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-collections"))
    api(project(":logging"))
    api(project(":model-core"))
    api(project(":publish"))
    api(project(":resources"))

    api(libs.jsr305)
    api(libs.inject)

    implementation(project(":functional"))
    implementation(project(":logging-api"))

    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.ivy)

    testImplementation(project(":native"))
    testImplementation(project(":process-services"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation(libs.slf4jApi)

    integTestRuntimeOnly(project(":resources-s3"))
    integTestRuntimeOnly(project(":resources-sftp"))
    integTestRuntimeOnly(project(":api-metadata"))

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Action class")
    }
    testFixturesApi(project(":core-api")) {
        because("Test fixtures export the RepositoryHandler class")
    }
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":dependency-management"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.sshdCore)
    testFixturesImplementation(libs.sshdScp)
    testFixturesImplementation(libs.sshdSftp)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("SamplesIvyPublishIntegrationTest test applies the java-library plugin.")
    }
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("IvyPublishCrossVersionIntegrationTest test applies the war plugin.")
    }
}

integTest.usesJavadocCodeSnippets = true
