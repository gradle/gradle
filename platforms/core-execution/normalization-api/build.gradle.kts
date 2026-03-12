plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Public API interfaces for input normalization"

dependencies {
    api(projects.baseServices)
    api(projects.stdlibJavaExtensions)
    api(libs.jspecify)
}
