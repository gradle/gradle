plugins {
    id("gradlebuild.distribution.api-java")
}

description = "API for Java compiler plugin used by Gradle's incremental compiler"

tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as? Int)
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

