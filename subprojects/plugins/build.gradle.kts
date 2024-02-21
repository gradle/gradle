plugins {
    id("gradlebuild.distribution.api-java")
}

errorprone {
    disabledChecks.addAll(
        "InlineMeSuggester", // 7 occurrences
        "UnusedMethod", // 7 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":logging"))
    api(project(":platform-jvm"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":base-annotations"))
    implementation(project(":hashing"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":model-core"))
    implementation(project(":persistent-cache"))
    implementation(project(":plugins-distribution"))
    implementation(project(":plugins-groovy"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-java-library"))
    implementation(project(":process-services"))
    implementation(project(":publish"))
    implementation(project(":snapshots"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.groovyTemplates)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsLang)

    testImplementation(project(":messaging"))
    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(libs.gson) {
        because("for unknown reason (bug in the Groovy/Spock compiler?) requires it to be present to use the Gradle Module Metadata test fixtures")
    }
    testImplementation(libs.jsoup)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":jvm-services")))

    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":base-services-groovy"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(testFixtures(project(":language-groovy")))
    testFixturesImplementation(project(":language-jvm"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":process-services"))
    testFixturesImplementation(project(":resources"))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(project(":enterprise-operations")))
    integTestImplementation(testFixtures(project(":language-java")))
    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":plugins-java")))
    integTestImplementation(testFixtures(project(":plugins-java-base")))
    integTestImplementation(testFixtures(project(":resources-http")))

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

description = """Provides core Gradle plugins, as well as many JVM-related plugins for building different types of Java and Groovy projects."""
