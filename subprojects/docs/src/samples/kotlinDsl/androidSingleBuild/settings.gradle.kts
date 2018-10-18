// tag::android[]
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
// end::android[]

rootProject.name = "android-single-build"

gradle.allprojects {
    repositories {
        google()
        jcenter()
    }
}
