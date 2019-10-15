/*
 * This is an init script for internal usage at Gradle Inc.
 */
val tasksWithBuildScansOnFailure = listOf("verifyTestFilesCleanup", "killExistingProcessesStartedByGradle", "tagBuild").map { listOf(it) }

if (!gradle.startParameter.systemPropertiesArgs.containsKey("disableScanPlugin")) {
    settingsEvaluated {
        pluginManager.withPlugin("com.gradle.enterprise") {
            extensions["gradleEnterprise"].withGroovyBuilder {
                "buildScan" {
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
        }
    }
}
