/*
 * This is an init script for internal usage at Gradle Inc.
 */
if (!gradle.startParameter.systemPropertiesArgs.containsKey("disableScanPlugin")) {
    rootProject {
        pluginManager.withPlugin("com.gradle.build-scan") {
            extensions["buildScan"].withGroovyBuilder {
                "publishAlways"()
                setProperty("server", "https://e.grdev.net")
            }
        }
    }
}
