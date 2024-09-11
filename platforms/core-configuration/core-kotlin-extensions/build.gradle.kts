plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-sam-with-receiver")
}

description = "Kotlin extensions to make working with Gradle :core and family more convenient"

dependencies {
    api(projects.buildOption)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.hashing)
    api(projects.loggingApi)

    api(libs.kotlinStdlib)

    implementation(projects.messaging)
    implementation(projects.resources)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.stdlibKotlinExtensions)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
