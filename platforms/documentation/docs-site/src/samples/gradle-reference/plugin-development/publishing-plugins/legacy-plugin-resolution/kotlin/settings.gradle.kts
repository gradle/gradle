pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "org.example") {
                useModule("org.example:custom-plugin:${requested.version}")
            }
        }
    }
}
