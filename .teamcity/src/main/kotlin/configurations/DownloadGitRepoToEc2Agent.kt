package configurations

import common.applyDefaultSettings
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.script

object DownloadGitRepoToEc2Agent : BuildType({
    val id = "Util_DownloadGitRepoToEc2Agent"
    name = "Download Git Repo to EC2 Agent"
    description = "Do nothing but downloading gradle/gradle repo to EC2 agents"

    applyDefaultSettings(artifactRuleOverride = "")

    params {
        param("defaultBranchName", "master")
    }

    steps {
        script {
            name = "DO_NOTHING"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = "echo 'Repo downloaded'"
        }
    }
})
