plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Publishing plugin for Ivy repositories"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":functional"))
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":file-collections"))
    implementation(project(":resources"))
    implementation(project(":publish"))
    implementation(project(":plugin-use"))
    implementation(project(":dependency-management"))

    implementation(libs.groovy) // for 'Closure' and 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.ivy)

    testImplementation(project(":native"))
    testImplementation(project(":process-services"))
    testImplementation(project(":snapshots"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation(project(":ear"))
    integTestImplementation(project(":war"))
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
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

integTest.usesJavadocCodeSnippets = true
