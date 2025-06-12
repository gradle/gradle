plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API to dynamically lookup services provided by Gradle modules"

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions)
}
