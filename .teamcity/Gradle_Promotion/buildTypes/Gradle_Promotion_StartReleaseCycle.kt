package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle

object Gradle_Promotion_StartReleaseCycle : BuildType({
    uuid = "355487d7-45b9-4387-9fc5-713e7683e6d0"
    name = "Master - Start Release Cycle"
    description = "Promotes a successful build on master as the start of a new release cycle on the release branch"

    artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
        promote-projects/gradle/build/reports/jdiff => jdiff
    """.trimIndent()

    params {
        text("gitUserEmail", "", label = "Git user.email Configuration", description = "Enter the git 'user.email' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
        text("confirmationCode", "", label = "Confirmation Code", description = "Enter the value 'startCycle' (no quotes) to confirm the promotion", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("gitUserName", "", label = "Git user.name Configuration", description = "Enter the git 'user.name' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
    }

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion__master_)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            name = "Promote"
            tasks = "clean promoteStartReleaseCycle"
            buildFile = ""
            gradleParams = """-PuseBuildReceipt -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" -Igradle/buildScanInit.gradle"""
            param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_ReadyforNightly_Trigger")) {
            buildRule = lastSuccessful("master")
            cleanDestination = true
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
