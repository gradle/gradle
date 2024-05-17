import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.DslContext

fun DslContext.initForTest() {
    // Set the project id here, so we can use methods on the DslContext
    parentProjectId = AbsoluteId("Gradle")
    projectId = AbsoluteId("Gradle_Master")
    settingsRootId = AbsoluteId("GradleMaster")
    settingsRoot.name = "GradleMaster"
    addParameters("Branch" to "master")
}
