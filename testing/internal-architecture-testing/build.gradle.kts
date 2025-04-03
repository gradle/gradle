plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures and tests for architecture testing Gradle code"

dependencies {
    api(platform(projects.distributionsDependencies))
    api(libs.archunit)
    api(libs.archunitJunit5Api)
}
