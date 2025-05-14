plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Groovy specific adaptations to the model management."

dependencies {
    api(projects.baseServices)
    api(projects.modelCore)
    api(projects.baseServicesGroovy)

    api(libs.jspecify)
    api(libs.groovy)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.coreApi)

    implementation(libs.guava)
    implementation(libs.jsr305)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelCore))

    testRuntimeOnly(projects.distributionsCore) {
        because("NonTransformedModelDslBackingTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
