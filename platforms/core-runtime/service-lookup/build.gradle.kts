plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal API to dynamically lookup services provided by Gradle modules"

dependencies {
    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions)
}
errorprone {
    nullawayEnabled = true
}
