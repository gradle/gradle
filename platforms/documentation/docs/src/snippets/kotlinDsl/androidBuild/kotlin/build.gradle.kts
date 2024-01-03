// tag::android[]
plugins {
    id("com.android.application") version "7.3.0" apply false
// end::android[]
    kotlin("android") version "1.9.20" apply false
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
        classpath("com.android.tools.build:gradle:7.3.0")
    }
}
// end::android-buildscript[]
