package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle

object Gradle_Promotion_PublishBranchSnapshotFromQuickFeedback : BuildType({
    uuid = "b7ecebd3-3812-4532-aa77-5679f9e9d6b3"
    name = "Publish Branch Snapshot (from Quick Feedback)"
    description = "Deploys a new wrapper for the selected build/branch. Does not update master or the documentation."

    artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
        **/build/git-checkout/build/build-receipt.properties
        **/build/distributions/*.zip => promote-build-distributions
        **/build/website-checkout/data/releases.xml
        **/build/git-checkout/build/reports/integTest/** => distribution-tests
        **/smoke-tests/build/reports/tests/** => post-smoke-tests
    """.trimIndent()

    params {
        param("branch.qualifier", "%dep.Gradle_Check_Stage_QuickFeedback_Trigger.teamcity.build.branch%")
        text("branch.to.promote", "%branch.qualifier%", label = "Branch to promote", description = "Type in the branch of gradle/gradle you want to promote. Leave the default value when promoting an existing build.", display = ParameterDisplay.PROMPT, allowEmpty = false)
    }

    vcs {
        root(Gradle_Promotion.vcsRoots.Gradle_Promotion_GradlePromotionBranches)

        checkoutMode = CheckoutMode.ON_SERVER
    }

    steps {
        gradle {
            name = "Promote"
            tasks = "promoteSnapshot"
            buildFile = ""
            gradleParams = """-PuseBuildReceipt "-PgitUserName=Gradleware Git Bot" "-PgitUserEmail=gradlewaregitbot@gradleware.com" -Igradle/buildScanInit.gradle -PpromotedBranch=%branch.qualifier% --build-cache "-Dgradle.cache.remote.url=%gradle.cache.remote.url%" "-Dgradle.cache.remote.username=%gradle.cache.remote.username%" "-Dgradle.cache.remote.password=%gradle.cache.remote.password%""""
        }
    }

    dependencies {
        artifacts(AbsoluteId("Gradle_Check_Stage_QuickFeedback_Trigger")) {
            buildRule = lastSuccessful("%branch.to.promote%")
            cleanDestination = true
            artifactRules = "build-receipt.properties => incoming-build-receipt/"
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
