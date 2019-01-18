package projects

import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.versionedSettings
import model.CIBuildModel
import model.Stage

class RootProject(model: CIBuildModel) : Project({
    uuid = model.projectPrefix.removeSuffix("_")
    id = AbsoluteId(uuid)
    parentId = AbsoluteId("Gradle")
    name = model.rootProjectName

    features {
        versionedSettings {
            id = "PROJECT_EXT_3"
            mode = VersionedSettings.Mode.ENABLED
            buildSettingsMode = VersionedSettings.BuildSettingsMode.PREFER_SETTINGS_FROM_VCS
            rootExtId = "Gradle_Branches_VersionedSettings"
            showChanges = true
            settingsFormat = VersionedSettings.Format.KOTLIN
            param("credentialsStorageType", "credentialsJSON")
        }
    }

    var prevStage: Stage? = null
    var deferredAlreadyDeclared = false
    model.stages.forEach { stage ->
        val containsDeferredTests = !stage.omitsSlowProjects && !deferredAlreadyDeclared
        deferredAlreadyDeclared = deferredAlreadyDeclared || containsDeferredTests
        buildType(StagePasses(model, stage,  prevStage, containsDeferredTests, uuid))
        subProject(StageProject(model, stage, containsDeferredTests, uuid))
        prevStage = stage
    }

    if (model.stages.map { stage -> stage.performanceTests }.flatten().isNotEmpty()) {
        subProject(WorkersProject(model))
    }

    buildTypesOrder = buildTypes
    subProjectsOrder = subProjects
})
