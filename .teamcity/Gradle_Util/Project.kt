package Gradle_Util

import Gradle_Util.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.versionedSettings

object Project : Project({
    uuid = "077cff89-d1d3-407b-acc0-88446a99dec7"
    id("Gradle_Util")
    parentId("Gradle")
    name = "Util"

    buildType(Gradle_Util_AdHocFunctionalTestWindows)
    buildType(Gradle_Util_AdHocFunctionalTestLinux)

    params {
        password("teamcity.user.bot-gradle.token", "credentialsJSON:6c130acc-332a-4dd3-98e0-7b699377dc7c", display = ParameterDisplay.HIDDEN)
    }

    features {
        versionedSettings {
            id = "PROJECT_EXT_16"
            mode = VersionedSettings.Mode.ENABLED
            buildSettingsMode = VersionedSettings.BuildSettingsMode.PREFER_SETTINGS_FROM_VCS
            rootExtId = "Gradle_Branches_VersionedSettings"
            showChanges = false
            settingsFormat = VersionedSettings.Format.KOTLIN
            storeSecureParamsOutsideOfVcs = true
        }
    }
})
