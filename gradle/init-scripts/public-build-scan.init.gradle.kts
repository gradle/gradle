// Gradle 6
settingsEvaluated {
    pluginManager.withPlugin("com.gradle.enterprise") {
        extensions["gradleEnterprise"].withGroovyBuilder {
            configureExtension(getProperty("buildScan"))
        }
    }
}
// Gradle 5 and earlier
rootProject {
    pluginManager.withPlugin("com.gradle.build-scan") {
        configureExtension(extensions.getByName("buildScan"))
    }
}

fun configureExtension(extension: Any) {
    extension.withGroovyBuilder {
        "publishAlways"()
        setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
        setProperty("termsOfServiceAgree", "yes")
    }
}
