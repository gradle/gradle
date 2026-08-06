plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "The lazy value types of the Gradle API: Provider, Property and friends"

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.declarativeDslApi)

    api(libs.jspecify)

    // Compile-time stubs for core-api types referenced by the file collection types
    // (Buildable, AntBuilderAware, ...). compileOnly so they never appear in published
    // metadata or the distribution; at runtime the real classes from :core-api are used.
    compileOnly(projects.fileApiStubs)
    // groovy.lang.Closure/DelegatesTo appear in FileCollection/FileTree signatures
    compileOnly(libs.groovy)

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

strictCompile {
    ignoreRawTypes() // raw Closure types used in the public API of FileCollection/FileTree
}
