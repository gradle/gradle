plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Build operations consumed by the Gradle Enterprise plugin"

dependencies {
    api(project(":build-operations"))

    implementation(project(":base-annotations"))
    implementation(libs.jsr305)
}
