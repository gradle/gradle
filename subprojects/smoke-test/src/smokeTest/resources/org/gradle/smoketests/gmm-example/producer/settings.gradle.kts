rootProject.name = "producer"

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

include("java-library")
include("android-library")
include("android-library-single-variant")
include("android-kotlin-library")
include("kotlin-library")
include("kotlin-multiplatform-library")
include("kotlin-multiplatform-android-library")