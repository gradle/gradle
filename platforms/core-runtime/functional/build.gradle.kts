plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to work with functional code, including data structures"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(libs.jspecify)
    api(libs.jsr305)
    api(projects.stdlibJavaExtensions)

    implementation(libs.guava)
    implementation(libs.fastutil)
}
