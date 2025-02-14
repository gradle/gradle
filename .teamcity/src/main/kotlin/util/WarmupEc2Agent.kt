package util

import common.BuildToolBuildJvm
import common.Os
import common.VersionedSettingsBranch
import common.buildToolGradleParameters
import common.gradleWrapper
import common.javaHome
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildFeatures.freeDiskSpace
import vcsroots.useAbsoluteVcs

object WarmupEc2Agent : BuildType({
    name = "Warmup EC2 Agent"
    id("Util_WarmupEc2Agent")

    vcs.useAbsoluteVcs(VersionedSettingsBranch.fromDslContext().vcsRootId())

    features {
        freeDiskSpace {
            // Lower the limit such that the agent work directories aren't cleaned during the AMI baking process
            requiredSpace = "100mb"
        }
    }

    params {
        param("defaultBranchName", "master")
        param("env.JAVA_HOME", javaHome(BuildToolBuildJvm, Os.LINUX))
    }

    steps {
        gradleWrapper {
            name = "Resolve all dependencies"
            tasks = "resolveAllDependencies"
            gradleParams =
                (
                    buildToolGradleParameters(isContinue = false) + listOf("--dependency-verification", "lenient")
                ).joinToString(separator = " ")
        }
    }
})
