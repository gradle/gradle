// tag::android[]
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id in listOf("com.android.application", "com.android.library", "com.android.test")) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

include("lib", "app")
// end::android[]

rootProject.name = "android-build"

gradle.allprojects {
    repositories {
        google()
        jcenter()
    }
}
