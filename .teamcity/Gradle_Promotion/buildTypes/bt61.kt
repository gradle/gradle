package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.schedule

object bt61 : BuildType({
    uuid = "1f5ca7f8-b0f5-41f9-9ba7-6d518b2822f0"
    name = "Release - Release Nightly Snapshot"
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

    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 1
            }
            branchFilter = ""
            triggerBuild = always()
            withPendingChangesOnly = false
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_ReadyforNightly_Trigger")) {
            buildRule = lastSuccessful("release")
            cleanDestination = true
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
