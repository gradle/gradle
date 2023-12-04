// tag::android[]
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if(requested.id.namespace == "com.android") {
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
        mavenCentral()
    }
}
