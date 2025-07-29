plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.classloaders)
    api(projects.coreApi)
    api(projects.core)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.stdlibJavaExtensions)
    api(projects.logging)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.modelReflect)
    api(projects.problemsApi)
    api(projects.softwareFeatures)

    api(libs.guava)
    api(libs.jspecify)

    implementation(projects.functional)
    implementation(projects.jvmServices)

    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.resourcesHttp))
    testImplementation(testFixtures(projects.core))

    integTestImplementation(projects.baseServicesGroovy)
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.groovyTest)

    integTestDistributionRuntimeOnly(projects.distributionsBasics) {
        because("Requires test-kit: 'java-gradle-plugin' is used in integration tests which always adds the test-kit dependency.")
    }
}

testFilesCleanup.reportOnly = true

description = """Provides functionality for resolving and managing plugins during their application to projects."""
tasks.isolatedProjectsIntegTest {
    enabled = false
}
