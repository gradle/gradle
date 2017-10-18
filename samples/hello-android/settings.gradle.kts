pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { setUrl("https://jcenter.bintray.com/") }
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
