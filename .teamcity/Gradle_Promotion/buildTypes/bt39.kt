package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.schedule

object bt39 : BuildType({
    uuid = "01432c63-861f-4d08-ae0a-7d127f63096e"
    name = "Master - Nightly Snapshot"
    description = "Promotes the latest successful change on 'master' as the new nightly"

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

    triggers {
        schedule {
            schedulingPolicy = daily {
                hour = 0
                timezone = ""
            }
            branchFilter = ""
            triggerBuild = always()
            withPendingChangesOnly = false
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_ReadyforNightly_Trigger")) {
            buildRule = lastSuccessful("master")
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
