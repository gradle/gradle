plugins {
    id("com.gradle.enterprise") version "3.5.2"
    id("com.gradle.enterprise.gradle-enterprise-conventions-plugin") version "0.7.2"
}

include("settings-plugin")

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
    }
}
