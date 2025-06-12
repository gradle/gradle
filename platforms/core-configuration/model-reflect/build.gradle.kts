plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of model reflection"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.logging)
    api(projects.persistentCache)
    api(projects.problemsApi)
    api(projects.stdlibJavaExtensions)

    api(libs.guava)
    api(libs.jspecify)

    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.inject)

    testFixturesApi(projects.internalIntegTesting)
    testFixturesImplementation(libs.guava)
}
