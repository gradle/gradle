import org.gradle.gradlebuild.test.integrationtests.integrationTestUsesSampleDir

plugins {
    gradlebuild.distribution.`api-java`
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":coreApi"))
    implementation(project(":files"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":dependencyManagement"))
    implementation(project(":workers"))
    implementation(project(":execution"))

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":coreApi")))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":processServices"))

    testFixturesApi(project(":core"))
    testFixturesApi(project(":fileCollections"))
    testFixturesApi(testFixtures(project(":modelCore")))
    testFixturesImplementation(library("guava"))
    testFixturesApi(testFixtures(project(":modelCore")))
    testFixturesApi(testFixtures(project(":diagnostics")))

    testRuntimeOnly(project(":distributionsCore")) {
        because("RuntimeShadedJarCreatorTest requires a distribution to access the ...-relocated.txt metadata")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/**"))
}

integrationTestUsesSampleDir("subprojects/platform-base/src/main")
