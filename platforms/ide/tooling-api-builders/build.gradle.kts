plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provider-side implementation for running tooling model builders"

errorprone {
    disabledChecks.addAll(
        "InlineMeSuggester", // 1 occurrences
    )
}

dependencies {
    implementation(projects.baseServicesGroovy) // for 'Specs'
    implementation(projects.coreApi)
    implementation(projects.dependencyManagement)
    implementation(projects.launcher)
    implementation(projects.problemsApi)
    implementation(projects.testingBase)
    implementation(projects.testingBaseInfrastructure)
    implementation(projects.testingJvm)
    implementation(projects.workers)

    implementation(libs.commonsIo)
    implementation(libs.guava)

    api(projects.baseServices)
    api(projects.buildEvents)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.enterpriseOperations)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.toolingApi)

    api(libs.jsr305)

    runtimeOnly(projects.compositeBuilds)
    runtimeOnly(libs.groovy) // for 'Closure'

    testCompileOnly(projects.toolchainsJvm) {
        because("JavaLauncher is required for mocking Test.")
    }
    testImplementation(projects.fileCollections)
    testImplementation(projects.platformJvm)
    testImplementation(testFixtures(projects.core))
}

strictCompile {
    ignoreDeprecations()
}
