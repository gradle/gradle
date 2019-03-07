package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs

object MasterSanityCheck : BuildType({
    uuid = "bf9b573a-6e5e-4db1-88b2-399e709026b5"
    id("Gradle_Promotion_MasterSanityCheck")
    name = "Master - Sanity Check"
    description = "Compilation and test execution of buildSrc"

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion__master_)

        checkoutMode = CheckoutMode.ON_SERVER
        cleanCheckout = true
        showDependenciesChanges = true
    }

    steps {
        gradle {
            tasks = "tasks -s"
            useGradleWrapper = true
            gradleParams = "-Igradle/buildScanInit.gradle"
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            param("ui.gradleRunner.gradle.build.file", "")
        }
    }

    triggers {
        vcs {
            branchFilter = ""
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
