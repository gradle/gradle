plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

dependencies {
    implementation(project(":base-annotations"))
    implementation(project(":files"))
    implementation(libs.slf4jApi)
}
