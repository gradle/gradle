plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedForStartup()

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":worker-services"))
}
