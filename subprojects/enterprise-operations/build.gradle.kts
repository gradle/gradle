plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    api(project(":build-operations"))

    implementation(libs.jsr305)
}
