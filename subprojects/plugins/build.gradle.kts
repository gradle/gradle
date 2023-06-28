plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":java-platform"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-groovy"))
    implementation(project(":diagnostics"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":snapshots"))
    implementation(project(":publish"))
    implementation(project(":ivy"))
    implementation(project(":maven"))
    implementation(project(":execution")) {
        because("We need it for BuildOutputCleanupRegistry")
    }

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":messaging"))
    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(libs.gson) {
        because("for unknown reason (bug in the Groovy/Spock compiler?) requires it to be present to use the Gradle Module Metadata test fixtures")
    }
    testImplementation(libs.jsoup)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":resources-http")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":jvm-services")))
    testImplementation(testFixtures(project(":language-jvm")))
    testImplementation(testFixtures(project(":language-java")))
    testImplementation(testFixtures(project(":language-groovy")))
    testImplementation(testFixtures(project(":diagnostics")))

    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":base-services-groovy"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(project(":language-jvm"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":process-services"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":enterprise-operations")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreDeprecations() // uses deprecated software model types
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true

description = """Provides core Gradle plugins such as the base plugin and version catalog plugin, as well as JVM-related plugins for building different types of Java and Groovy projects."""
