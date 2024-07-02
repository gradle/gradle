plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Extensions to the Java language that are used across the Gradle codebase"

gradlebuildJava.usedInWorkers()

dependencies {
    compileOnly(libs.jetbrainsAnnotations)

    api(libs.jsr305)
}
