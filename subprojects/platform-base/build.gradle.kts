plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":files"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":workers"))
    implementation(project(":execution"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))

    testFixturesApi(project(":core"))
    testFixturesApi(project(":file-collections"))
    testFixturesApi(testFixtures(project(":model-core")))
    testFixturesImplementation(libs.guava)
    testFixturesApi(testFixtures(project(":model-core")))
    testFixturesApi(testFixtures(project(":diagnostics")))

    testRuntimeOnly(project(":distributions-core")) {
        because("RuntimeShadedJarCreatorTest requires a distribution to access the ...-relocated.txt metadata")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

integTest.usesJavadocCodeSnippets = true

description = """Provides general purpose base types and interfaces for modeling projects, and provides runtime and language support."""
