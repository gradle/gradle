plugins {
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

allprojects {
    group = "org.gradle.client"
    // Version must be strictly x.y.z and >= 1.0.0 for all native packaging to work
    version = "1.0.0"
}
