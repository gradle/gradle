plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools for creating secure hashes for files and other content"

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    implementation(libs.guava)
}
