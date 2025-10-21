plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for generating parsers from Antlr grammars."

errorprone {
    disabledChecks.addAll(
        "Finally", // 1 occurrences
    )
}

dependencies {
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileOperations)
    api(projects.files)
    api(projects.modelCore)
    api(projects.requestHandlerWorker)
    api(projects.stdlibJavaExtensions)

    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.baseServices)
    implementation(projects.languageJava)
    implementation(projects.logging)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJavaLibrary)

    implementation(libs.guava)
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
