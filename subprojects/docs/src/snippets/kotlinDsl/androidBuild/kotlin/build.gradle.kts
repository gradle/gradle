// tag::android[]
plugins {
    id("com.android.application") version "4.1.2" apply false
// end::android[]
    kotlin("android") version "1.6.10" apply false
    kotlin("android.extensions") version "1.6.10" apply false
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
        classpath("com.android.tools.build:gradle:4.1.2")
    }
}
// end::android-buildscript[]
