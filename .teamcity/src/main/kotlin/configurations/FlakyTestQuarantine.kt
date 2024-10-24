package configurations

import common.Arch
import common.BuildToolBuildJvm
import common.KillProcessMode.KILL_PROCESSES_STARTED_BY_GRADLE
import common.Os
import common.applyDefaultSettings
import common.buildScanTagParam
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.functionalTestExtraParameters
import common.functionalTestParameters
import common.getBuildScanCustomValueParam
import common.gradleWrapper
import common.killProcessStep
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage
import model.StageName
import model.TestType

class FlakyTestQuarantine(model: CIBuildModel, stage: Stage, os: Os, arch: Arch = Arch.AMD64) : OsAwareBaseGradleBuildType(os = os, stage = stage, init = {
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

    if (os == Os.LINUX) {
        steps {
            script {
                // Because we exclude tests in `distributions-integ-tests` below, `@Flaky` won't work in that subproject.
                // Here we check the existence of `@Flaky` annotation to make sure nobody use that annotation in `distributions-integ-tests` subproject.
                name = "MAKE_SURE_NO_@FLAKY_IN_DISTRIBUTIONS_INTEG_TESTS"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                scriptContent = "cd testing/distributions-integ-tests/src && ! grep 'org.gradle.test.fixtures.Flaky' -r ."
            }
        }
    }

    testsWithOs.forEachIndexed { index, testCoverage ->
        val extraParameters = functionalTestExtraParameters(listOf("FlakyTestQuarantine"), os, arch, testCoverage.testJvmVersion.major.toString(), testCoverage.vendor.name)
        val parameters = (
            buildToolGradleParameters() +
                listOf(
                    "-PflakyTests=only",
                    "-x", ":docs:platformTest",
                    "-x", ":docs:configCacheTest",
                    "-x", ":distributions-integ-tests:quickTest",
                    "-x", ":distributions-integ-tests:platformTest",
                    "-x", ":distributions-integ-tests:configCacheTest"
                ) +
                listOf(extraParameters) +
                functionalTestParameters(os, arch) +
                listOf(buildScanTagParam(functionalTestTag), stage.getBuildScanCustomValueParam())
            ).joinToString(separator = " ")
        steps {
            gradleWrapper {
                name = "FLAKY_TEST_QUARANTINE_${testCoverage.testType.name.uppercase()}_${testCoverage.testJvmVersion.name.uppercase()}"
                val testTaskName =
                    if (testCoverage.testType == TestType.isolatedProjects) "isolatedProjectsIntegTest" else "${testCoverage.testType.name}Test"
                tasks = "${if (index == 0) "clean " else ""}$testTaskName"
                gradleParams = parameters
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }
        killProcessStep(KILL_PROCESSES_STARTED_BY_GRADLE, os, arch, executionMode = BuildStep.ExecutionMode.ALWAYS)
    }

    steps {
        checkCleanM2AndAndroidUserHome(os)
    }

    applyDefaultDependencies(model, this, true)
})
