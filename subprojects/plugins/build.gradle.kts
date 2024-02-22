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
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":language-groovy"))
    implementation(project(":plugins-distribution"))
    implementation(project(":plugins-groovy"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-java-library"))
    implementation(project(":diagnostics"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":test-suites-base"))
    implementation(project(":snapshots"))
    implementation(project(":publish"))
    implementation(project(":ivy"))
    implementation(project(":maven"))
    implementation(project(":execution")) {
        because("We need it for BuildOutputCleanupRegistry")
    }
    implementation(project(":toolchains-jvm"))
    implementation(project(":plugins-jvm-test-suite"))

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.ant)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    integTestImplementation(testFixtures(project(":enterprise-operations")))
    integTestImplementation(testFixtures(project(":language-java")))
    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":plugins-java")))
    integTestImplementation(testFixtures(project(":plugins-java-base")))
    integTestImplementation(testFixtures(project(":resources-http")))

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
    ignoreDeprecations() // uses deprecated software model types
}

packageCycles {
    excludePatterns.add("org/gradle/**")
}

testFilesCleanup.reportOnly = true

description = """Provides core Gradle plugins, as well as many JVM-related plugins for building different types of Java and Groovy projects."""
