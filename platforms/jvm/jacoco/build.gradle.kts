plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugin and integration with JaCoCo code coverage"

errorprone {
    disabledChecks.addAll(
        "ReferenceEquality", // 3 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":platform-jvm"))
    api(project(":reporting"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":logging-api"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-jvm-test-suite"))
    implementation(project(":process-services"))
    implementation(project(":test-suites-base"))
    implementation(project(":testing-jvm"))

    implementation(libs.commonsLang)
    implementation(libs.guava)

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testFixturesImplementation(libs.jsoup)
    testFixturesImplementation(libs.groovyXml)

    testImplementation(project(":internal-testing"))
    testImplementation(project(":resources"))
    testImplementation(project(":internal-integ-testing"))
    testImplementation(project(":language-java"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes()
}

packageCycles {
    excludePatterns.add("org/gradle/internal/jacoco/*")
    excludePatterns.add("org/gradle/testing/jacoco/plugins/*")
}
