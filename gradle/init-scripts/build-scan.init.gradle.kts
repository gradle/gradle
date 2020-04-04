/*
 * This is an init script for internal usage at Gradle Inc.
 */
val tasksWithBuildScansOnFailure = listOf("verifyTestFilesCleanup", "killExistingProcessesStartedByGradle").map { listOf(it) }

if (!gradle.startParameter.systemPropertiesArgs.containsKey("disableScanPlugin")) {
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
}

fun configureExtension(extension: Any) {
    extension.withGroovyBuilder {
        if (gradle.startParameter.taskNames in tasksWithBuildScansOnFailure) {
            "publishOnFailure"()
        } else {
            "publishAlways"()
        }
        setProperty("server", "https://e.grdev.net")

        if (!System.getProperty("slow.internet.connection", "false").toBoolean()) {
            setProperty("captureTaskInputFiles", true)
        }
    }
}
