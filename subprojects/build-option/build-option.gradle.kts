plugins {
    gradlebuild.distribution.`core-api-java`
}

gradlebuildJava.usedInWorkers()

dependencies {
    implementation(project(":cli"))

    implementation(project(":baseAnnotations"))
    implementation(library("commons_lang"))
}
