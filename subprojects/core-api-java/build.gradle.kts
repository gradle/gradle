plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Kotlin extensions to make working with Gradle :core and family more convenient"

dependencies {
    implementation(projects.baseServices)
    compileOnly(libs.jetbrainsAnnotations)
}
