// == Define locations for build logic ==
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
includeBuild("../platforms")
includeBuild("../build-logic")

// == Define locations for components ==
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
includeBuild("../user-feature")

// == Define the inner structure of this component ==
rootProject.name = "android-app"
include("app")
