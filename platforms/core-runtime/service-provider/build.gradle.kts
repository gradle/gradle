plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "An API for providing internal services for Gradle modules"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.javaLanguageExtensions)

    api(libs.errorProneAnnotations)
}
