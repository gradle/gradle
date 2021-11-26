package configurations

import common.BuildToolBuildJvm
import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.functionalTestExtraParameters
import common.functionalTestParameters
import common.gradleWrapper
import common.killProcessStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import model.CIBuildModel
import model.Stage
import model.StageNames

class FlakyTestQuarantine(model: CIBuildModel, stage: Stage, os: Os) : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_FlakyQuarantine_${os.name.lowercase().capitalize()}")
    name = "Flaky Test Quarantine - ${os.name.lowercase().capitalize()}"
    description = "Run all flaky tests skipped multiple times"

    applyDefaultSettings(os, BuildToolBuildJvm, 60)

    val testsWithOs = model.stages.filter {
        it.stageName in listOf(
            StageNames.QUICK_FEEDBACK_LINUX_ONLY,
            StageNames.QUICK_FEEDBACK,
            StageNames.READY_FOR_MERGE,
            StageNames.READY_FOR_NIGHTLY
        )
    }.flatMap { it.functionalTests }.filter { it.os == os }

    testsWithOs.forEach { testCoverage ->
        val extraParameters = functionalTestExtraParameters("FlakyTestQuarantine", os, testCoverage.testJvmVersion.major.toString(), testCoverage.vendor.name)
        val parameters = (
            buildToolGradleParameters(true) +
                listOf("-PflakyTestQuarantine") +
                listOf(extraParameters) +
                functionalTestParameters(os)
            ).joinToString(separator = " ")
        steps {
            gradleWrapper {
                name = "FLAKY_TEST_QUARANTINE_${testCoverage.testJvmVersion.name.uppercase()}"
                tasks = "${testCoverage.testType.name}Test"
                gradleParams = parameters
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }
        killProcessStep("KILL_PROCESSES_STARTED_BY_GRADLE", true)
    }

    steps {
        checkCleanM2AndAndroidUserHome(os)
    }
})
