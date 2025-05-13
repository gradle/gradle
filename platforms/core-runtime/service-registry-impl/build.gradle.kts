plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implementation of the service registry framework"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.serviceLookup)
    api(projects.serviceProvider)

    api(libs.jspecify)

    implementation(projects.concurrent)
    implementation(projects.stdlibJavaExtensions)

    implementation(libs.inject)
}

