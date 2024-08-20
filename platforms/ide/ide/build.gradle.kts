plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins and model builders for integration with Eclipse and IntelliJ IDEs"

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 1 occurrences
        "LoopOverCharArray", // 1 occurrences
        "ObjectEqualsForPrimitives", // 3 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.stdlibJavaExtensions)
    api(projects.modelCore)
    api(projects.platformJvm)
    api(projects.serviceProvider)
    api(projects.toolingApi)

    api(libs.guava)
    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.baseServicesGroovy)
    implementation(projects.ear)
    implementation(projects.languageJava)
    implementation(projects.loggingApi)
    implementation(projects.platformBase)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.serviceLookup)
    implementation(projects.war)

    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)


    runtimeOnly(projects.languageJvm)
    runtimeOnly(projects.testingBase)
    runtimeOnly(projects.testingJvm)

    testFixturesApi(projects.baseServices) {
        because("test fixtures export the Action class")
    }
    testFixturesApi(projects.logging) {
        because("test fixtures export the ConsoleOutput class")
    }
    testFixturesApi(projects.toolingApi) {
        because("test fixtures export the EclipseWorkspace and EclipseWorkspaceProject classes")
    }
    testFixturesImplementation(projects.dependencyManagement)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.modelCore)
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(libs.xmlunit)

    testImplementation(projects.dependencyManagement)
    testImplementation(libs.xmlunit)
    testImplementation(libs.equalsverifier)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.dependencyManagement))
    testImplementation(testFixtures(projects.languageGroovy))

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsJvm)
}

strictCompile {
    ignoreRawTypes()
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ide/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/eclipse/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/idea/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/eclipse/model/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/idea/model/internal/*")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
