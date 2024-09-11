plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Tooling API model builders for native builds"

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(projects.coreApi)
    api(projects.core)
    api(projects.ide) {
        because("To pick up various builders (which should live somewhere else)")
        api(projects.toolingApi)
    }

    implementation(projects.baseServices)
    implementation(projects.fileCollections)
    implementation(projects.languageNative)
    implementation(projects.platformNative)
    implementation(projects.testingNative)

    implementation(libs.guava)

    testImplementation(testFixtures(projects.platformNative))

    crossVersionTestDistributionRuntimeOnly(projects.distributionsNative)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
