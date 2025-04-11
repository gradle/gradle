// tag::avoid-this[]
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("java")
}

repositories {
    mavenCentral()
}
// end::avoid-this[]

dependencies {
    implementation("org:doesntexist:1.0.0")
}
