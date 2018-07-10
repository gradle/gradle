
include("cli", "core")

pluginManagement {
    repositories {
        kotlinDev()
        gradlePluginPortal()
    }
}

gradle.rootProject {
    allprojects {
        repositories {
      	    kotlinDev()
        }
    }
}
