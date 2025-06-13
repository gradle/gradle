plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Gradle build option parser."

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(projects.cli)
    api(projects.stdlibJavaExtensions)
    api(projects.messaging)

    api(libs.jspecify)

    implementation(projects.baseServices)
}
