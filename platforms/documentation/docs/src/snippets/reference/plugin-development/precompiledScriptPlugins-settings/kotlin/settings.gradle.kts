// tag::main[]
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("./settings-plugin")
}

plugins {
    id("my-plugin")
}
// end::main[]

rootProject.name = "precompiled-script-plugins-in-settings"
