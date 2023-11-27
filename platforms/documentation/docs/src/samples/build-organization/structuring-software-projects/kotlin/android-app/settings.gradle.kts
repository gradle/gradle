// == Define locations for build logic ==
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
    includeBuild("../build-logic")
}

// == Define locations for components ==
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
includeBuild("../platforms")
includeBuild("../user-feature")

// == Define the inner structure of this component ==
rootProject.name = "android-app"
include("app")
