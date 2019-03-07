package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle

object Gradle_Promotion_ReleaseSnapshotFromQuickFeedback : BuildType({
    uuid = "eeff4410-1e7d-4db6-b7b8-34c1f2754477"
    name = "Release - Release Nightly Snapshot (from Quick Feedback)"
    description = "Deploys the latest successful change on 'release' as a new release nightly snapshot"

    artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
        **/build/git-checkout/build/build-receipt.properties
        **/build/distributions/*.zip => promote-build-distributions
        **/build/website-checkout/data/releases.xml
        **/build/git-checkout/build/reports/integTest/** => distribution-tests
        **/smoke-tests/build/reports/tests/** => post-smoke-tests
    """.trimIndent()

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion__master_)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            name = "Promote"
            tasks = "promoteReleaseNightly"
            buildFile = ""
            gradleParams = """-PuseBuildReceipt "-PgitUserName=Gradleware Git Bot" "-PgitUserEmail=gradlewaregitbot@gradleware.com" -Igradle/buildScanInit.gradle --build-cache "-Dgradle.cache.remote.url=%gradle.cache.remote.url%" "-Dgradle.cache.remote.username=%gradle.cache.remote.username%" "-Dgradle.cache.remote.password=%gradle.cache.remote.password%""""
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_QuickFeedback_Trigger")) {
            buildRule = lastSuccessful("release")
            cleanDestination = true
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
