import gradlebuild.integrationtests.integrationTestUsesSampleDir

plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":files"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":workers"))
    implementation(project(":execution"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":coreApi")))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))

    testFixturesApi(project(":core"))
    testFixturesApi(project(":fileCollections"))
    testFixturesApi(testFixtures(project(":modelCore")))
    testFixturesImplementation(libs.guava)
    testFixturesApi(testFixtures(project(":modelCore")))
    testFixturesApi(testFixtures(project(":diagnostics")))

    testRuntimeOnly(project(":distributions-core")) {
        because("RuntimeShadedJarCreatorTest requires a distribution to access the ...-relocated.txt metadata")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/**"))
}

integrationTestUsesSampleDir("subprojects/platform-base/src/main")
