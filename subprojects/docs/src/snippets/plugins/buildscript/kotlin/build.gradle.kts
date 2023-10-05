// tag::buildscript_block[]
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.5")
    }
}

plugins {
    id("com.jfrog.bintray")
}
// end::buildscript_block[]
