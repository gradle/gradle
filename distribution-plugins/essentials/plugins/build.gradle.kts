plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:execution") {
        because("We need it for BuildOutputCleanupRegistry")
    }
    implementation(project(":workers"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-groovy"))
    implementation(project(":diagnostics"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation("org.gradle:messaging")
    testImplementation("org.gradle:native")
    testImplementation("org.gradle:resources")
    testImplementation(libs.gson) {
        because("for unknown reason (bug in the Groovy/Spock compiler?) requires it to be present to use the Gradle Module Metadata test fixtures")
    }
    testImplementation(libs.jsoup)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:jvm-services"))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))
    testImplementation(testFixtures(project(":language-groovy")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures("org.gradle:platform-native"))

    testFixturesImplementation(testFixtures("org.gradle:core"))
    testFixturesImplementation("org.gradle:base-services-groovy")
    testFixturesImplementation("org.gradle:file-collections")
    testFixturesImplementation("org.gradle:process-services")
    testFixturesImplementation("org.gradle:resources")
    testFixturesImplementation(project(":language-jvm"))
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(project(":model-core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-jvm")
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.add("org/gradle/**")
}

integTest.usesSamples.set(true)
testFilesCleanup.reportOnly.set(true)
