plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

dependencies {
    implementation(project(":base-annotations"))
}
