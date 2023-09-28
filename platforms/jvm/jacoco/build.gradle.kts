plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugin and integration with JaCoCo code coverage"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":language-jvm"))
    implementation(project(":platform-base"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":plugins"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":reporting"))
    implementation(project(":file-collections"))
    implementation(project(":plugins-jvm-test-suite"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

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
