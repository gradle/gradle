pluginManagement {
    repositories {
        kotlinDev()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin2js") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

gradle.rootProject {
    repositories {
        kotlinDev()
    }
}
