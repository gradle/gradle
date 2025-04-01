plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Groovy specific enhancements to Gradle APIs"

dependencies {
    api(projects.baseServicesGroovy)

    api(libs.groovy)
    api(libs.jspecify)

    implementation(libs.guava)

    testImplementation(projects.modelCore)
}
