plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and domain objects for testing native code"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":diagnostics"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":platform-native"))
    implementation(project(":language-native"))
    implementation(project(":testing-base"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)

    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":testing-base")))
    testImplementation(testFixtures(project(":language-native")))
    testImplementation(testFixtures(project(":ide")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

