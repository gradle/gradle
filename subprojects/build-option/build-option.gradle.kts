plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))

    implementation(project(":baseAnnotations"))
    implementation(libs.commonsLang)
}
