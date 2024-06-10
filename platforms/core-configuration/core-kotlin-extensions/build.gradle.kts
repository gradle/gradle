plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Kotlin extensions to make working with Gradle :core and family more convenient"

dependencies {
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.hashing)

    api(libs.kotlinStdlib)

    implementation(projects.baseServices)
    implementation(projects.messaging)
    implementation(projects.resources)
    implementation(projects.serviceProvider)
    implementation(projects.stdlibKotlinExtensions)
}
