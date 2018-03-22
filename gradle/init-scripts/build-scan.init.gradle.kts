/*
 * This is an init script for internal usage at Gradle Inc.
 */
initscript {
    repositories {
        gradlePluginPortal()
        maven(url = "https://repo.gradle.org/gradle/gradlecom-libs-snapshots-local/")
    }
    dependencies {
        classpath("com.gradle:build-scan-plugin:1.13-rc-1-20180316150839-master")
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
