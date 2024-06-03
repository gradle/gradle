plugins {
    id("gradlebuild.distribution.api-java")
}

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 1 occurrences
        "ModifiedButNotUsed", // 1 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
        "UnusedMethod", // 5 occurrences
    )
}
dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":logging"))
    api(project(":model-core"))

    api(libs.guava)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":dependency-management"))
    implementation(project(":execution"))

    implementation(libs.commonsLang)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))

    testFixturesApi(project(":file-collections"))
    testFixturesApi(testFixtures(project(":diagnostics")))
    testFixturesApi(testFixtures(project(":model-core")))

    testFixturesImplementation(libs.guava)

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
