package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs

object Gradle_Promotion_AllBranchesStartReleaseCycleTest : BuildType({
    uuid = "59823634-f79d-4c11-bbca-782957a7d65c"
    name = "Master - Start Release Cycle Test"
    description = "Test for Start Release Cycle pipeline"

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion_GradlePromotionBranches)
    }

    steps {
        gradle {
            name = "PromoteTest"
            tasks = "clean promoteStartReleaseCycle"
            buildFile = ""
            gradleParams = "-PconfirmationCode=startCycle -Igradle/buildScanInit.gradle -PtestRun=1"
            param("teamcity.tool.jacoco", "%teamcity.tool.jacoco.DEFAULT%")
        }
    }

    triggers {
        vcs {
            branchFilter = "+:master"
        }
        schedule {
            schedulingPolicy = daily {
                hour = 3
            }
            branchFilter = "+:master"
            triggerBuild = always()
            withPendingChangesOnly = false
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
