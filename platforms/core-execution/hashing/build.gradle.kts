plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools for creating secure hashes for files and other content"

gradleModule {
    // org.gradle.internal.nativeintegration.filesystem.Stat is used in workers
    usedInWorkers = true
}

dependencies {
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    implementation(libs.guava)
}
