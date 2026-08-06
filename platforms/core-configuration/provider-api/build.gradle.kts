plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "The lazy value types of the Gradle API: Provider, Property and friends"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.declarativeDslApi)

    api(libs.jspecify)

    // Javadoc-only: downstream modules whose types are referenced by {@link ...} in this module's docs.
    javadocReferences(projects.coreApi)
    javadocReferences(projects.baseServices)
}

gradleModule {
    computedRuntimes {
        client = true
        daemon = true
        worker = true
    }
}

errorprone {
    nullawayEnabled = true
}
