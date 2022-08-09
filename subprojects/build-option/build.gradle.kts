plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Gradle build option parser."

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))

    implementation(project(":base-annotations"))
    implementation(libs.commonsLang)
}
