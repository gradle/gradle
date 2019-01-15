/*
 * This is an init script for internal usage at Gradle Inc.
 */
val tasksWithBuildScansOnFailure = listOf("verifyTestFilesCleanup", "killExistingProcessesStartedByGradle", "tagBuild").map { listOf(it)}

if (!gradle.startParameter.systemPropertiesArgs.containsKey("disableScanPlugin")) {
    rootProject {
        pluginManager.withPlugin("com.gradle.build-scan") {
            extensions["buildScan"].withGroovyBuilder {
                if (gradle.startParameter.taskNames in tasksWithBuildScansOnFailure) {
                    "publishOnFailure"()
                } else {
                    "publishAlways"()
                }
                setProperty("server", "https://e.grdev.net")

                if (metaClass.hasProperty(delegate, "captureTaskInputFiles") != null) {
                    if (!System.getProperty("slow.internet.connection", "false").toBoolean()) {
                        setProperty("captureTaskInputFiles", true)
                    }
                }
            }
        }
    }
}
