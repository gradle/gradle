/*
 * This is an init script for internal usage at Gradle Inc.
 */
initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.gradle:build-scan-plugin:1.13")
    }
}

if (!gradle.startParameter.systemPropertiesArgs.containsKey("disableScanPlugin")) {
    rootProject {
        pluginManager.withPlugin("com.gradle.build-scan") {
            extensions["buildScan"].withGroovyBuilder {
                if (gradle.startParameter.taskNames in listOf(listOf("verifyTestFilesCleanup"), listOf("killExistingProcessesStartedByGradle"))) {
                    "publishOnFailure"()
                } else {
                    "publishAlways"()
                }
                setProperty("server", "https://e.grdev.net")
            }
        }
    }
}
