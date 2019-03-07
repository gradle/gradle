package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle

object Gradle_Promotion_MilestoneMaster : BuildType({
    uuid = "2ffb238a-08af-4f95-b863-9830d2bc3872"
    name = "Release - Milestone"
    description = "Promotes the latest successful change on 'release' as the new snapshot"

    artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
        promote-projects/gradle/gradle/build/gradle-checkout/build/build-receipt.properties
        promote-projects/gradle/gradle/build/distributions/*.zip => promote-build-distributions
        promote-projects/gradle/gradle/build/website-checkout/data/releases.xml
        promote-projects/gradle/build/git-checkout/build/reports/distributions/integTest/** => distribution-tests
        promote-projects/gradle/smoke-tests/build/reports/tests/** => post-smoke-tests
    """.trimIndent()

    params {
        text("gitUserEmail", "", label = "Git user.email Configuration", description = "Enter the git 'user.email' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
        param("confirmationCode", "")
        text("gitUserName", "", label = "Git user.name Configuration", description = "Enter the git 'user.name' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
    }

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion__master_)

        checkoutMode = CheckoutMode.ON_SERVER
        showDependenciesChanges = true
    }

    steps {
        gradle {
            name = "Promote"
            tasks = "promoteMilestone -s -PconfirmationCode=milestone"
            buildFile = ""
            gradleParams = """-PuseBuildReceipt -i "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" -Igradle/buildScanInit.gradle --build-cache "-Dgradle.cache.remote.url=%gradle.cache.remote.url%" "-Dgradle.cache.remote.username=%gradle.cache.remote.username%" "-Dgradle.cache.remote.password=%gradle.cache.remote.password%""""
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_ReadyforRelease_Trigger")) {
            buildRule = lastSuccessful("release")
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
