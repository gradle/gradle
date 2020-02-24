package Gradle_Util.buildTypes

import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.Requirement
import jetbrains.buildServer.configs.kotlin.v2019_2.RequirementType

object WarmupEc2Agent : BuildType({
    uuid = "980bce31-2a3e-4563-9717-7c03e184f4a4"
    name = "Warmup EC2 Agent"
    id("Gradle_Util_WarmupEc2Agent")

    vcs {
        root(AbsoluteId("Gradle_Branches_GradlePersonalBranches"))
        checkoutMode = CheckoutMode.ON_AGENT
    }

    params {
        param("defaultBranchName", "master")
    }

    steps {
        gradleWrapper {
            name = "Resolve all dependencies"
            tasks = "resolveAllDependencies"
        }
    }

    requirements {
        requirement(Requirement(RequirementType.EQUALS, "teamcity.agent.name", "ec2-agent1"))
    }

})
