rootProject.name = "maven-publish"

pluginManagement {
    repositories {
        kotlinDev()
        gradlePluginPortal()
    }
}

gradle.rootProject {
    repositories {
        kotlinDev()
    }
}
