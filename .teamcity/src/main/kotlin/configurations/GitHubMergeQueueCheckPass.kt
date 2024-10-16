package configurations

import common.VersionedSettingsBranch
import common.uuidPrefix
import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import model.CIBuildModel
import model.StageName

class GitHubMergeQueueCheckPass(model: CIBuildModel) : BaseGradleBuildType(init = {
    id("${model.projectId}_GitHubMergeQueueCheckPass")
    uuid = "${DslContext.uuidPrefix}_${model.projectId}_GitHubMergeQueueCheckPass"
    name = "GitHub Merge Queue Check Pass"
    type = Type.COMPOSITE

    vcs {
        root(AbsoluteId(VersionedSettingsBranch.fromDslContext().vcsRootId()))
        checkoutMode = CheckoutMode.ON_AGENT
    }

    features {
        enablePullRequestFeature()
        publishBuildStatusToGithub(model)
    }

    if (!VersionedSettingsBranch.fromDslContext().isExperimental) {
        triggers.vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.DO_NOT_USE
            branchFilter = """
+:gh-readonly-queue/${model.branch.branchName}/*
+:${model.branch.branchName}
"""
        }
    }

    dependencies {
        snapshot(RelativeId(stageTriggerId(model, StageName.READY_FOR_NIGHTLY))) {
            onDependencyFailure = FailureAction.FAIL_TO_START
            onDependencyCancel = FailureAction.FAIL_TO_START
        }
    }
})
