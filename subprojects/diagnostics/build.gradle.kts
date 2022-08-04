plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains project diagnostics or report tasks, e.g. help, project report, dependency report and similar"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":functional"))
    implementation(project(":logging"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":snapshots"))
    implementation(project(":dependency-management"))
    implementation(project(":base-services-groovy"))
    implementation(project(":build-option"))

    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.jatl)

    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)

    testFixturesApi(testFixtures(project(":platform-native")))
    testFixturesApi(testFixtures(project(":logging")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))  {
        because("There are integration tests that assert that all the tasks of a full distribution are reported (these should probably move to ':integTests').")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/api/reporting/model/internal/*")
    excludePatterns.add("org/gradle/api/reporting/dependencies/internal/*")
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}
