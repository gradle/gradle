rootProject {
    pluginManager.withPlugin("com.gradle.build-scan") {
        extensions["buildScan"].withGroovyBuilder {
            "publishAlways"()
            setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
            setProperty("termsOfServiceAgree", "yes")
        }
    }
}
