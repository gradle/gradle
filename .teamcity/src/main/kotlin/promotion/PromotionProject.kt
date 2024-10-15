package promotion

import common.BuildToolBuildJvm
import common.OpenJdk11
import common.OpenJdk17
import common.OpenJdk8
import common.Os
import common.VersionedSettingsBranch
import common.cleanupRule
import common.javaHome
import jetbrains.buildServer.configs.kotlin.Project

class PromotionProject(branch: VersionedSettingsBranch) : Project({
    id("Promotion")
    name = "Promotion"

    cleanupRule(historyDays = 14, artifactsDays = 7)

    buildType(SanityCheck)
    buildType(PublishNightlySnapshot(branch))
    buildType(PublishNightlySnapshotFromQuickFeedback(branch))
    buildType(PublishNightlySnapshotFromQuickFeedbackStepCheckReady(branch))
    buildType(PublishNightlySnapshotFromQuickFeedbackStepUpload(branch))
    buildType(PublishNightlySnapshotFromQuickFeedbackStepPromote(branch))
    buildType(PublishBranchSnapshotFromQuickFeedback)
    buildType(PublishMilestone(branch))

    if (branch.isMaster) {
        buildType(StartReleaseCycle)
        buildType(StartReleaseCycleTest)
    } else {
        buildType(PublishReleaseCandidate(branch))
        buildType(PublishFinalRelease(branch))
    }

    if (branch.isRelease || branch.isExperimental) {
        buildType(PublishNightlyDocumentation(branch))
    }

    params {
        password("env.ORG_GRADLE_PROJECT_gradleS3AccessKey", "%gradleS3AccessKey%")
        password("env.ORG_GRADLE_PROJECT_gradleS3SecretKey", "%gradleS3SecretKey%")
        password("env.ORG_GRADLE_PROJECT_artifactoryUserPassword", "%gradle.internal.repository.build-tool.publish.password%")
        password("env.DOTCOM_DEV_DOCS_AWS_ACCESS_KEY", "%dotcomDevDocsAwsAccessKey%")
        password("env.DOTCOM_DEV_DOCS_AWS_SECRET_KEY", "%dotcomDevDocsAwsSecretKey%")
        password("env.ORG_GRADLE_PROJECT_sdkmanToken", "%sdkmanToken%")
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
        // https://github.com/gradle/gradle-private/issues/4504
        param("env.JDK8", javaHome(OpenJdk8, Os.LINUX))
        param("env.JDK11", javaHome(OpenJdk11, Os.LINUX))
        param("env.JDK17", javaHome(OpenJdk17, Os.LINUX))
        param("env.ORG_GRADLE_PROJECT_artifactoryUserName", "%gradle.internal.repository.build-tool.publish.username%")
        password("env.ORG_GRADLE_PROJECT_infrastructureEmailPwd", "%infrastructureEmailPwd%")
        param("env.ORG_GRADLE_PROJECT_sdkmanKey", "8ed1a771bc236c287ad93c699bfdd2d7")
        param("env.PGP_SIGNING_KEY", "%pgpSigningKey%")
        param("env.PGP_SIGNING_KEY_PASSPHRASE", "%pgpSigningPassphrase%")
    }

    buildTypesOrder = arrayListOf(
        SanityCheck
    )
})
