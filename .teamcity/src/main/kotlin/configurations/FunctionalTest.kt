package configurations

import common.functionalTestExtraParameters
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildFeatures.parallelTests
import model.CIBuildModel
import model.Stage
import model.StageNames
import model.StageNames.READY_FOR_RELEASE
import model.TestCoverage
import model.TestParallelizationMode
import model.TestType

const val functionalTestTag = "FunctionalTest"

class FunctionalTest(
    model: CIBuildModel,
    id: String,
    name: String,
    description: String,
    val testCoverage: TestCoverage,
    stage: Stage,
    testParallelizationMode: TestParallelizationMode,
    numberOfBatches: Int = 1,
    subprojects: List<String> = listOf(),
    extraParameters: String = "",
    extraBuildSteps: BuildSteps.() -> Unit = {},
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(stage = stage, init = {
    this.name = name
    this.description = description
    this.id(id)
    val testTasks = getTestTaskName(testCoverage, subprojects)

    if (testParallelizationMode == TestParallelizationMode.ParallelTesting) {
        features {
            parallelTests {
                this.numberOfBatches = numberOfBatches
            }
        }
    }

    if (name.contains("(configuration-cache)")) {
        requirements {
            doesNotContain("teamcity.agent.name", "ec2")
            // US region agents have name "EC2-XXX"
            doesNotContain("teamcity.agent.name", "EC2")
        }
    }

    applyTestDefaults(
        model, this, testTasks, notQuick = !testCoverage.isQuick, os = testCoverage.os,
        buildJvm = testCoverage.buildJvm,
        arch = testCoverage.arch,
        extraParameters = listOf(
            functionalTestExtraParameters(
                functionalTestTag,
                testCoverage.os,
                testCoverage.arch,
                testCoverage.testJvmVersion.major.toString(),
                testCoverage.vendor.name
            ),
            "-PflakyTests=${determineFlakyTestStrategy(stage)}",
            (if (testParallelizationMode == TestParallelizationMode.TestDistribution) "-DenableTestDistribution=%enableTestDistribution% -DtestDistributionPartitionSizeInSeconds=%testDistributionPartitionSizeInSeconds%" else ""),
            extraParameters
        ).filter { it.isNotBlank() }.joinToString(separator = " "),
        timeout = testCoverage.testType.timeout,
        extraSteps = extraBuildSteps,
        preSteps = preBuildSteps
    )

    if (testCoverage.testType == TestType.soak || testTasks.contains("plugins:")) {
        failureConditions {
            // JavaExecDebugIntegrationTest.debug session fails without debugger might cause JVM crash
            // Some soak tests produce OOM exceptions
            javaCrash = false
        }
    }
})

private fun determineFlakyTestStrategy(stage: Stage): String {
    val stageName = StageNames.values().first { it.stageName == stage.stageName.stageName }
    // See gradlebuild.basics.FlakyTestStrategy
    return if (stageName.ordinal < READY_FOR_RELEASE.ordinal) "exclude" else "include"
}

fun getTestTaskName(testCoverage: TestCoverage, subprojects: List<String>): String {
    val testTaskName = "${testCoverage.testType.name}Test"
    return when {
        subprojects.isEmpty() -> {
            testTaskName
        }
        else -> {
            subprojects.joinToString(" ") { "$it:$testTaskName" }
        }
    }
}
