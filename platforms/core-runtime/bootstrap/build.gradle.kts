plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Java 6-compatible entry point of the Gradle launcher. See :launcher project for the rest."

gradlebuildJava.usedForStartup()

dependencies {
    implementation(projects.javaLanguageExtensions)
    implementation(project(":worker-services"))
}
