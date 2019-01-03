// tag::android[]
plugins {
    id("com.android.application") version "3.2.0" apply false
// end::android[]
    kotlin("android") version "1.2.71" apply false
    kotlin("android.extensions") version "1.2.71" apply false
// tag::android[]
}
// end::android[]

// tag::android-buildscript[]
buildscript {
    repositories {
        google()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.2.0")
    }
}
// end::android-buildscript[]
