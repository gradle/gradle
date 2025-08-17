pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("./settings-plugin")
}

plugins {
    id("my-plugin")
}

rootProject.name = "precompiled-script-plugins-in-settings"
