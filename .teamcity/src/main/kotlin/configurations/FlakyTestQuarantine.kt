package configurations

import common.Arch
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
import model.StageName

class FlakyTestQuarantine(model: CIBuildModel, stage: Stage, os: Os, arch: Arch = Arch.AMD64) : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_FlakyQuarantine_${os.asName()}_${arch.asName()}")
    name = "Flaky Test Quarantine - ${os.asName()} ${arch.asName()}"
    description = "Run all flaky tests skipped multiple times"

    applyDefaultSettings(os = os, arch = arch, buildJvm = BuildToolBuildJvm, timeout = 180)

    val testsWithOs = model.stages.filter {
        it.stageName in listOf(
            StageName.QUICK_FEEDBACK_LINUX_ONLY,
            StageName.QUICK_FEEDBACK,
            StageName.PULL_REQUEST_FEEDBACK,
            StageName.READY_FOR_NIGHTLY,
        )
    }.flatMap { it.functionalTests }.filter { it.os == os }

    testsWithOs.forEach { testCoverage ->
        val extraParameters = functionalTestExtraParameters("FlakyTestQuarantine", os, arch, testCoverage.testJvmVersion.major.toString(), testCoverage.vendor.name)
        val parameters = (
            buildToolGradleParameters(true) +
                listOf("-PflakyTests=only") +
                listOf(extraParameters) +
                functionalTestParameters(os) +
                listOf(buildScanTag(functionalTestTag))
            ).joinToString(separator = " ")
        steps {
            gradleWrapper {
                name = "FLAKY_TEST_QUARANTINE_${testCoverage.testType.name.uppercase()}_${testCoverage.testJvmVersion.name.uppercase()}"
                tasks = "${testCoverage.testType.name}Test"
                gradleParams = parameters
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }
        killProcessStep("KILL_PROCESSES_STARTED_BY_GRADLE", os, arch)
    }

    steps {
        checkCleanM2AndAndroidUserHome(os)
    }

    applyDefaultDependencies(model, this, true)
})
