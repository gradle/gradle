plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utilities for parsing command line arguments"

dependencies {
    compileOnly(libs.jspecify)
}

gradleModule {
    targetRuntimes {
        usedInClient = true
    }
}

errorprone {
    nullawayEnabled = true
}
