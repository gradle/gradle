rootProject.name = "consumer"

pluginManagement {
    includeBuild("../plugin") // path to the plugin project

    repositories {
        gradlePluginPortal() // optional fallback
    }
}