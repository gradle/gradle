plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations consumed by the Develocity plugin"

dependencies {
    api(project(":build-operations"))
    api(project(":enterprise-workers"))

    implementation(project(":base-annotations"))
    implementation(libs.jsr305)
}
