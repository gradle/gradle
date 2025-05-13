plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Shared classes for projects requiring GPG support"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    api(projects.resources)

    api(libs.bouncycastlePgp)
    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.time)
    implementation(projects.loggingApi)

    implementation(libs.bouncycastleProvider)
    implementation(libs.guava)

    testRuntimeOnly(projects.logging)
}
