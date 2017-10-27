pluginManagement {
    repositories {
        // Use the Gradle Plugin Portal as a regular maven repository
        // allowing the plugin resolution strategy below to route the
        // plugin request to artifact coordinates.
        maven { url = uri("https://plugins.gradle.org/m2") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlin2js") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}
