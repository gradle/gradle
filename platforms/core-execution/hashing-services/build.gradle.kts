plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Implementation of hashing services with caching support"

gradleModule {
    computedRuntimes {
        client = true
        daemon = true
        worker = true
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.hashing)
    api(projects.native)
    api(projects.persistentCache)
    api(projects.scopedPersistentCache)
    api(projects.stdlibJavaExtensions)

    implementation(projects.fileTemp)
    implementation(projects.files)
    implementation(projects.serialization)

    implementation(libs.guava)

    compileOnly(libs.jspecify)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.hashing))
    testImplementation(projects.internalTesting)
}
