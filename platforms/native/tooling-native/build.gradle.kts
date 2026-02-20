plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Tooling API model builders for native builds"

dependencies {
    api(projects.coreApi)
    api(projects.core)
    api(projects.serviceProvider)
    api(projects.toolingApi)
    api(projects.toolingNativeModelImpls)

    implementation(projects.baseServices)
    implementation(projects.fileCollections)
    implementation(projects.ide) {
        because("To pick up various builders (which should live somewhere else)")
    }
    implementation(projects.ideModelImpls)
    implementation(projects.languageNative)
    implementation(projects.platformNative)
    implementation(projects.testingNative)


    implementation(libs.guava)

    testImplementation(testFixtures(projects.platformNative))

    crossVersionTestImplementation(projects.internalIntegTesting)
    crossVersionTestImplementation(testFixtures(projects.platformNative))

    crossVersionTestDistributionRuntimeOnly(projects.distributionsNative)
}
