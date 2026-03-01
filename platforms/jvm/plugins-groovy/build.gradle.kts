plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains plugins for building Groovy projects."

dependencies {
    api(libs.jspecify)
    api(libs.inject)

    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.fileOperations)
    api(projects.languageJava)
    api(projects.modelCore)
    api(projects.buildProcessServices)

    implementation(projects.classloaders)
    implementation(projects.core)
    implementation(projects.fileCollections)
    implementation(projects.groovyCompilerWorker)
    implementation(projects.groovydocWorker)
    implementation(projects.jvmCompilerWorker)
    implementation(projects.jvmServices)
    implementation(projects.languageGroovy)
    implementation(projects.languageJvm)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.reporting)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.toolchainsJvm)
    implementation(projects.toolchainsJvmShared)

    implementation(libs.guava)

    runtimeOnly(libs.groovy)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.languageGroovy))

    testRuntimeOnly(projects.distributionsJvm)

    integTestImplementation(testFixtures(projects.pluginsJavaBase))

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("The full distribution is required to run the GroovyToJavaConversionIntegrationTest")
    }

    crossVersionTestImplementation(projects.internalIntegTesting)

    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
