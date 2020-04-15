rootProject.name = "consumer"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

include("java-app")
include("kotlin-app")
include("native-app")
include("android-app")
include("android-kotlin-app")