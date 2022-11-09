plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Gradle build option parser."

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))
    implementation(project(":base-services"))

    implementation(project(":base-annotations"))
    implementation(project(":messaging"))
    implementation(libs.commonsLang)
}
