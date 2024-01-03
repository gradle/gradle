plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared build cache classes"

dependencies {
    api(project(":files"))

    implementation(project(":base-annotations"))
}
