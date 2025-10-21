package configurations

import common.BuildToolBuildJvm
import common.FlakyTestStrategy
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
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage
import model.StageName
import model.TestCoverage
import model.TestType

class FlakyTestQuarantineTrigger(
    model: CIBuildModel,
    flakyTestQuarantineProject: FlakyTestQuarantineProject,
) : OsAwareBaseGradleBuildType(os = flakyTestQuarantineProject.os, init = {
        id("${model.projectId}_FlakyQuarantine_${flakyTestQuarantineProject.os.asName()}_Trigger")

        name = flakyTestQuarantineProject.name + " (Trigger)"
        type = Type.COMPOSITE

        applyDefaultSettings()

        dependencies {
            snapshotDependencies(
                flakyTestQuarantineProject.buildTypes.map {
                    it as BaseGradleBuildType
                },
            )
        }
    })

class FlakyTestQuarantineProject(
    model: CIBuildModel,
    stage: Stage,
    val os: Os,
) : Project({
        id("${model.projectId}_FlakyQuarantine_${os.asName()}")
        name = "Flaky Test Quarantine - ${os.asName()}"

        model.stages
            .filter { it.stageName <= StageName.READY_FOR_RELEASE }
            .flatMap { it.functionalTests }
            .filter { it.os == os && !it.testType.crossVersionTests }
            .forEach {
                buildType(FlakyTestQuarantine(model, stage, it))
            }

        model.stages
            .filter { it.stageName <= StageName.READY_FOR_RELEASE }
            .flatMap { stage -> stage.specificBuilds.map { it.create(model, stage, FlakyTestStrategy.ONLY) } }
            .filter { it.os == os }
            .filter { it is SmokeTests || it is SmokeIdeTests }
            .forEach(this::buildType)
    })

class FlakyTestQuarantine(
    model: CIBuildModel,
    stage: Stage,
    testCoverage: TestCoverage,
) : OsAwareBaseGradleBuildType(os = testCoverage.os, stage = stage, init = {
        val os = testCoverage.os
        val arch = testCoverage.arch
        id("${model.projectId}_FlakyQuarantine_${testCoverage.asId(model)}")
        name = "Flaky Test Quarantine - ${testCoverage.asName()}"
        description = "Run all flaky tests skipped multiple times"

        applyDefaultSettings(os = os, arch = arch, buildJvm = BuildToolBuildJvm, timeout = 180)

        if (os == Os.LINUX) {
            steps {
                script {
                    // Because we exclude tests in `distributions-integ-tests` below, `@Flaky` won't work in that subproject.
                    // Here we check the existence of `@Flaky` annotation to make sure nobody use that annotation in `distributions-integ-tests` subproject.
                    name = "MAKE_SURE_NO_@FLAKY_IN_DISTRIBUTIONS_INTEG_TESTS"
                    executionMode = BuildStep.ExecutionMode.ALWAYS
                    scriptContent =
                        "cd testing/distributions-integ-tests/src && ! grep 'org.gradle.test.fixtures.Flaky' -r ."
                }
            }
        }

        val extraParameters =
            functionalTestExtraParameters(
                listOf("FlakyTestQuarantine"),
                os,
                arch,
                testCoverage.testJvmVersion.major.toString(),
                testCoverage.vendor.name.lowercase(),
            )
        val parameters =
            (
                buildToolGradleParameters() +
                    listOf(
                        "-PflakyTests=${FlakyTestStrategy.ONLY}",
                        "-x",
                        ":docs:platformTest",
                        "-x",
                        ":docs:configCacheTest",
                        "-x",
                        ":distributions-integ-tests:quickTest",
                        "-x",
                        ":distributions-integ-tests:platformTest",
                        "-x",
                        ":distributions-integ-tests:configCacheTest",
                    ) +
                    listOf(extraParameters) +
                    functionalTestParameters(os, arch) +
                    listOf(buildScanTagParam(FUNCTIONAL_TEST_TAG), stage.getBuildScanCustomValueParam())
            ).joinToString(separator = " ")
        steps {
            gradleWrapper {
                name =
                    "FLAKY_TEST_QUARANTINE_${testCoverage.testType.name.uppercase()}_${testCoverage.testJvmVersion.name.uppercase()}"
                val testTaskName =
                    if (testCoverage.testType ==
                        TestType.ISOLATED_PROJECTS
                    ) {
                        "isolatedProjectsIntegTest"
                    } else {
                        "${testCoverage.testType.asCamelCase()}Test"
                    }
                tasks = "clean $testTaskName"
                gradleParams = parameters
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }
        killProcessStep(KILL_PROCESSES_STARTED_BY_GRADLE, os, arch, executionMode = BuildStep.ExecutionMode.ALWAYS)

        steps {
            checkCleanM2AndAndroidUserHome(os)
        }

        applyDefaultDependencies(model, this)
    })
