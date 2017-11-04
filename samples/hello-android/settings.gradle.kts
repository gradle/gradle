pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://jcenter.bintray.com/") }
        maven { url = uri("https://maven.google.com/") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

rootProject.buildFileName = "build.gradle.kts"
