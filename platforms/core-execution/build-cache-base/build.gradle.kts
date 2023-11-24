plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared build cache classes"

dependencies {
    api(project(":build-cache-spi"))

    implementation(project(":base-annotations"))
    implementation(project(":files"))
}
