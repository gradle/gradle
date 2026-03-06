plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal implementation of Gradle authentication container"

dependencies {
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.credentials)
    api(projects.credentialsApi)
    api(projects.serviceProvider)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions) {
        because("Provides @NullMarked")
    }

    testImplementation(testFixtures(projects.core))
}
