/*
 * This is an init script for internal usage at Gradle Inc.
 */
if ("disableScanPlugin" !in gradle.startParameter.systemPropertiesArgs) {
    rootProject {
        pluginManager.withPlugin("com.gradle.build-scan") {
            extensions["buildScan"].withGroovyBuilder {
                "publishAlways"()
                setProperty("server", "https://e.grdev.net")
            }
        }
    }
}
