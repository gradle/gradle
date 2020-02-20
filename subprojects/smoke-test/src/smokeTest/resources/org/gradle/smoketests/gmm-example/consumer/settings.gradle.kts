rootProject.name = "consumer"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap/")
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
