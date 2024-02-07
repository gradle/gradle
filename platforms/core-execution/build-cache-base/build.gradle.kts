plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared build cache classes"

dependencies {
    implementation(projects.baseAnnotations)
    implementation(projects.hashing)
    implementation(projects.files)
}
