plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Gradle build option parser."

dependencies {
    api(projects.cli)
    api(projects.stdlibJavaExtensions)

    api(libs.jspecify)

    implementation(projects.baseServices)
}
