settingsEvaluated {
    pluginManager.withPlugin("com.gradle.enterprise") {
        extensions["gradleEnterprise"].withGroovyBuilder {
            "buildScan" {
                "publishAlways"()
                setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
                setProperty("termsOfServiceAgree", "yes")
            }
        }
    }
}
