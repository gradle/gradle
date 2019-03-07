package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle

object Gradle_Promotion_MasterNightlySnapshotManual : BuildType({
    uuid = "9a55bec1-4e70-449b-8f45-400093505afb"
    name = "Master - Nightly Snapshot (from Quick Feedback)"
    description = "Promotes the latest change on 'master' that passed the 'Quick Feedback' stage as new nightly. This build configuration can be triggered manually if there are issues further down the pipeline we can ignore temporarily."

    artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
        **/build/git-checkout/build/build-receipt.properties
        **/build/distributions/*.zip => promote-build-distributions
        **/build/website-checkout/data/releases.xml
        **/build/gradle-checkout/build/reports/integTest/** => distribution-tests
        **/smoke-tests/build/reports/tests/** => post-smoke-tests
    """.trimIndent()

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion__master_)

        checkoutMode = CheckoutMode.ON_SERVER
        cleanCheckout = true
        showDependenciesChanges = true
    }

    steps {
        gradle {
            name = "Promote"
            tasks = "promoteNightly -s"
            buildFile = ""
            gradleParams = """-PuseBuildReceipt -i "-PgitUserName=Gradleware Git Bot" "-PgitUserEmail=gradlewaregitbot@gradleware.com" -Igradle/buildScanInit.gradle --build-cache "-Dgradle.cache.remote.url=%gradle.cache.remote.url%" "-Dgradle.cache.remote.username=%gradle.cache.remote.username%" "-Dgradle.cache.remote.password=%gradle.cache.remote.password%""""
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_QuickFeedback_Trigger")) {
            buildRule = lastSuccessful("master")
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
