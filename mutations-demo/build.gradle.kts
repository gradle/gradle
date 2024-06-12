plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.gradle.declarative.dsl.core)
    implementation(libs.gradle.prototypes.plugins.android)
}
