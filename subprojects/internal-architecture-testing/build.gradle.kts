plugins {
    id("gradlebuild.internal.java")
}

description = "Collection of test fixtures and tests for architecture testing Gradle code"

dependencies {
    api(platform(project(":distributions-dependencies")))
    api(libs.archunitJunit5) {
        because("This is what we use to write our architecture tests")
    }
    api(libs.junit5JupiterApi) {
        because("This is what we use to write our architecture tests")
    }
}
