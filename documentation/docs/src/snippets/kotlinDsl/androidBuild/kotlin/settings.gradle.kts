pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
// tag::android[]
include("lib", "app")
// end::android[]

rootProject.name = "android-build"

gradle.allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
