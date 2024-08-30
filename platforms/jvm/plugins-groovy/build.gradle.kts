plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Contains plugins for building Groovy projects."

dependencies {
    api(libs.jsr305)
    api(libs.groovy)
    api(libs.inject)

    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.languageJava)
    api(projects.modelCore)
    api(projects.buildProcessServices)

    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.fileCollections)
    implementation(projects.jvmServices)
    implementation(projects.languageGroovy)
    implementation(projects.languageJvm)
    implementation(projects.logging)
    implementation(projects.platformJvm)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.reporting)
    implementation(projects.toolchainsJvm)
    implementation(projects.toolchainsJvmShared)

    implementation(libs.guava)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.languageGroovy))

    testRuntimeOnly(projects.distributionsJvm)

    integTestImplementation(testFixtures(projects.pluginsJavaBase))

    integTestDistributionRuntimeOnly(projects.distributionsFull) {
        because("The full distribution is required to run the GroovyToJavaConversionIntegrationTest")
    }
    crossVersionTestDistributionRuntimeOnly(projects.distributionsCore)
}

integTest.usesJavadocCodeSnippets.set(true)
tasks.isolatedProjectsIntegTest {
    enabled = false
}
