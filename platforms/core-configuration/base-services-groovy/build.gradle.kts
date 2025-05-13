plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of generic services and utilities specific for Groovy."

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)
    api(libs.groovy)
    api(libs.guava)

    testImplementation(testFixtures(projects.core))
}
