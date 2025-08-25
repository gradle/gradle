plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utilities for parsing command line arguments"

dependencies {
    implementation(libs.jspecify)
}

gradleModule {
    targetRuntimes {
        usedInClient = true
    }
}

errorprone {
    nullawayEnabled = true
}
