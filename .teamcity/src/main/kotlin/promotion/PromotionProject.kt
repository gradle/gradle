package promotion

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.javaHome
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

class PromotionProject(branch: VersionedSettingsBranch) : Project({
    id("Promotion")
    name = "Promotion"

    buildType(SanityCheck)
    buildType(PublishNightlySnapshot(branch))
    buildType(PublishNightlySnapshotFromQuickFeedback(branch))
    buildType(PublishBranchSnapshotFromQuickFeedback)
    buildType(PublishMilestone(branch))

    if (branch == VersionedSettingsBranch.MASTER) {
        buildType(StartReleaseCycle)
        buildType(StartReleaseCycleTest)
    } else {
        buildType(PublishReleaseCandidate(branch))
        buildType(PublishFinalRelease(branch))
    }

    params {
        password("env.ORG_GRADLE_PROJECT_gradleS3SecretKey", "%gradleS3SecretKey%")
        password("env.ORG_GRADLE_PROJECT_artifactoryUserPassword", "%artifactoryUserPassword%")
        param("env.ORG_GRADLE_PROJECT_gradleS3AccessKey", "AKIAQBZWBNAJCJGCAMFL")
        password("env.DOTCOM_DEV_DOCS_AWS_SECRET_KEY", "%dotcomDevDocsAwsSecretKey%")
        param("env.DOTCOM_DEV_DOCS_AWS_ACCESS_KEY", "AKIAX5VJCER2X7DPYFXF")
        password("env.ORG_GRADLE_PROJECT_sdkmanToken", "%sdkmanToken%")
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
        param("env.ORG_GRADLE_PROJECT_artifactoryUserName", "bot-build-tool")
        password("env.ORG_GRADLE_PROJECT_infrastructureEmailPwd", "%infrastructureEmailPwd%")
        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "8ed1a771bc236c287ad93c699bfdd2d7")
    }

    buildTypesOrder = arrayListOf(
        SanityCheck
    )
})
