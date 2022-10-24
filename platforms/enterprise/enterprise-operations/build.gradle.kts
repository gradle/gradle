plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations consumed by the Develocity plugin"

dependencies {
    api(project(":build-operations"))

    implementation(project(":base-annotations"))

    api(libs.jsr305)
}
