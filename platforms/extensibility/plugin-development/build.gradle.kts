plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Gradle plugin development plugins"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "LoopOverCharArray", // 1 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.modelCore)
    api(projects.platformJvm)
    api(projects.problemsApi)
    api(projects.resources)
    api(projects.toolchainsJvmShared)
    api(projects.workers)

    api(libs.groovy)
    api(libs.gson)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.buildOption)
    implementation(projects.dependencyManagement)
    implementation(projects.execution)
    implementation(projects.hashing)
    implementation(projects.ivy)
    implementation(projects.languageJava)
    implementation(projects.languageJvm)
    implementation(projects.loggingApi)
    implementation(projects.maven)
    implementation(projects.messaging)
    implementation(projects.modelGroovy)
    implementation(projects.pluginsGroovy)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.pluginsJavaLibrary)
    implementation(projects.pluginsJvmTestSuite)
    implementation(projects.pluginUse)
    implementation(projects.processServices)
    implementation(projects.publish)
    implementation(projects.testingJvm)
    implementation(projects.toolchainsJvm)

    implementation(libs.asm)
    implementation(libs.guava)

    testImplementation(projects.fileCollections)
    testImplementation(projects.enterpriseOperations)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    integTestImplementation(projects.baseServicesGroovy)

    integTestImplementation(testFixtures(projects.modelCore))
    integTestImplementation(testFixtures(projects.toolingApi))

    integTestImplementation(libs.groovyTest)
    integTestImplementation(libs.jetbrainsAnnotations)

    integTestLocalRepository(projects.toolingApi) {
        because("Required by GradleImplDepsCompatibilityIntegrationTest")
    }

    testRuntimeOnly(projects.distributionsBasics) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsBasics)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsBasics)

    testFixturesImplementation(projects.modelCore)
    testFixturesImplementation(projects.logging)
    testFixturesImplementation(libs.gson)
    testFixturesImplementation(projects.baseServices)
}

integTest.usesJavadocCodeSnippets = true

strictCompile {
    ignoreDeprecations()
}
