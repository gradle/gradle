import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext

fun DslContext.initForTest() {
    // Set the project id here, so we can use methods on the DslContext
    parentProjectId = AbsoluteId("Gradle")
    projectId = AbsoluteId("Gradle_Master")
    settingsRootId = AbsoluteId("GradleMaster")
    settingsRoot.name = "GradleMaster"
    addParameters("Branch" to "master")
}
