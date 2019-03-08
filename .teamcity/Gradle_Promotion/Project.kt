package Gradle_Promotion

import Gradle_Promotion.buildTypes.MasterSanityCheck
import Gradle_Promotion.buildTypes.PublishBranchSnapshotFromQuickFeedback
import Gradle_Promotion.buildTypes.PublishFinalRelease
import Gradle_Promotion.buildTypes.PublishMilestone
import Gradle_Promotion.buildTypes.PublishNightlySnapshot
import Gradle_Promotion.buildTypes.PublishNightlySnapshotFromQuickFeedback
import Gradle_Promotion.buildTypes.PublishReleaseCandidate
import Gradle_Promotion.buildTypes.StartReleaseCycle
import Gradle_Promotion.buildTypes.StartReleaseCycleTest
import Gradle_Promotion.vcsRoots.Gradle_Promotion_GradlePromotionBranches
import Gradle_Promotion.vcsRoots.Gradle_Promotion__master_
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.versionedSettings

object Project : Project({
    uuid = "16c9f3e3-36a9-4596-a35c-70a3c7a2c5c8"
    id("Gradle_Promotion")
    parentId("Gradle")
    name = "Promotion"

    vcsRoot(Gradle_Promotion_GradlePromotionBranches)
    vcsRoot(Gradle_Promotion__master_)

    val nightlyMasterSnapshot = PublishNightlySnapshot(uuid = "01432c63-861f-4d08-ae0a-7d127f63096e", branch = "master", hour = 0)
    val masterSnapshotFromQuickFeedback = PublishNightlySnapshotFromQuickFeedback(uuid = "9a55bec1-4e70-449b-8f45-400093505afb", branch = "master")
    val nightlyReleaseSnapshot = PublishNightlySnapshot(uuid = "1f5ca7f8-b0f5-41f9-9ba7-6d518b2822f0", branch = "release", hour = 1)
    val releaseSnapshotFromQuickFeedback = PublishNightlySnapshotFromQuickFeedback(uuid = "eeff4410-1e7d-4db6-b7b8-34c1f2754477", branch = "release")

    buildType(PublishBranchSnapshotFromQuickFeedback)
    buildType(PublishMilestone)
    buildType(PublishReleaseCandidate)
    buildType(nightlyReleaseSnapshot)
    buildType(StartReleaseCycle)
    buildType(PublishFinalRelease)
    buildType(nightlyMasterSnapshot)
    buildType(StartReleaseCycleTest)
    buildType(MasterSanityCheck)
    buildType(masterSnapshotFromQuickFeedback)
    buildType(releaseSnapshotFromQuickFeedback)

    params {
        password("env.ORG_GRADLE_PROJECT_gradleS3SecretKey", "credentialsJSON:0f1f842f-df6c-4db7-8271-f1f73c823aed")
        password("env.ORG_GRADLE_PROJECT_artifactoryUserPassword", "credentialsJSON:2b7529cd-77cd-49f4-9416-9461f6ac9018")
        param("env.ORG_GRADLE_PROJECT_gradleS3AccessKey", "AKIAJUN6ZAPAEO3BC7AQ")
        password("env.DOTCOM_DEV_DOCS_AWS_SECRET_KEY", "credentialsJSON:ed0db35e-2034-444c-a9b1-d966b9abe89b")
        param("env.DOTCOM_DEV_DOCS_AWS_ACCESS_KEY", "AKIAJFJBF5BXLBNI3E5A")
        password("env.ORG_GRADLE_PROJECT_sdkmanToken", "credentialsJSON:64e60515-68db-4bbd-aeae-ba2e058ac3cb")
        param("env.JAVA_HOME", "%linux.java11.openjdk.64bit%")
        param("env.ORG_GRADLE_PROJECT_artifactoryUserName", "bot-build-tool")
        password("env.ORG_GRADLE_PROJECT_infrastructureEmailPwd", "credentialsJSON:ea637ef1-7607-40a4-be39-ef1aa8bc5af0")
        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "8ed1a771bc236c287ad93c699bfdd2d7")
    }

    features {
        versionedSettings {
            id = "PROJECT_EXT_15"
            mode = VersionedSettings.Mode.ENABLED
            buildSettingsMode = VersionedSettings.BuildSettingsMode.PREFER_SETTINGS_FROM_VCS
            rootExtId = "Gradle_Branches_VersionedSettings"
            showChanges = false
            settingsFormat = VersionedSettings.Format.KOTLIN
            storeSecureParamsOutsideOfVcs = true
        }
    }
    buildTypesOrder = arrayListOf(
        MasterSanityCheck,
        nightlyMasterSnapshot,
        masterSnapshotFromQuickFeedback,
        StartReleaseCycle,
        nightlyReleaseSnapshot,
        releaseSnapshotFromQuickFeedback,
        PublishBranchSnapshotFromQuickFeedback,
        PublishMilestone,
        PublishReleaseCandidate,
        PublishFinalRelease
    )
})
