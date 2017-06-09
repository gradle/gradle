package projects

import jetbrains.buildServer.configs.kotlin.v10.Project
import jetbrains.buildServer.configs.kotlin.v10.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v10.projectFeatures.versionedSettings
import model.CIBuildModel

object RootProject : Project({
    uuid = "Gradle_Check"
    extId = uuid
    parentId = "Gradle"
    name = "Check"

    features {
        versionedSettings {
            id = "PROJECT_EXT_3"
            mode = VersionedSettings.Mode.ENABLED
            buildSettingsMode = VersionedSettings.BuildSettingsMode.PREFER_SETTINGS_FROM_VCS
            rootExtId = "Gradle_Branches_VersionedSettings"
            showChanges = false
            settingsFormat = VersionedSettings.Format.KOTLIN
            param("credentialsStorageType", "credentialsJSON")
        }
    }

    CIBuildModel.stages.forEach { stage ->
        subProject(StageProject(CIBuildModel.stages.indexOf(stage) + 1, stage))
    }
})
