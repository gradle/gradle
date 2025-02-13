plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Kotlin extensions to make working with Gradle :core and family more convenient"

dependencies {
    implementation(projects.baseServices)
    compileOnly(libs.jetbrainsAnnotations)
    api(libs.kotlinStdlib)
    implementation(projects.stdlibKotlinExtensions)
}
