package Gradle_Check

import Gradle_Check.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project
import jetbrains.buildServer.configs.kotlin.v10.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v10.projectFeatures.VersionedSettings.*
import jetbrains.buildServer.configs.kotlin.v10.projectFeatures.versionedSettings

object Project : Project({
    uuid = "f6beca08-1be5-41d3-9bb6-bc26a1e8b770"
    extId = "Gradle_Check"
    parentId = "Gradle"
    name = "Check"

    template(Gradle_Check_TestCoverageEmbeddedWindows)
    template(Gradle_Check_TestCoverageForkedWindows)
    template(Gradle_Check_TestCoverageForkedLinux)
    template(Gradle_Check_TestCoverageEmbeddedLinux)

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
})
