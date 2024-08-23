plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Adds support for generating parsers from Antlr grammars."

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "Finally", // 1 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.modelCore)

    api(libs.inject)

    implementation(projects.baseServices)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJavaLibrary)

    implementation(libs.guava)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    compileOnly("antlr:antlr:2.7.7") {
        because("this dependency is downloaded by the antlr plugin")
    }

    runtimeOnly(projects.languageJvm)
    runtimeOnly(projects.workers)

    testImplementation(projects.baseServicesGroovy)
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.fileCollections)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/antlr/internal/*")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
