package configurations

import common.functionalTestExtraParameters
import jetbrains.buildServer.configs.kotlin.BuildSteps
import model.CIBuildModel
import model.Stage
import model.StageName
import model.TestCoverage

const val functionalTestTag = "FunctionalTest"

class FunctionalTest(
    model: CIBuildModel,
    id: String,
    name: String,
    description: String,
    val testCoverage: TestCoverage,
    stage: Stage,
    enableTestDistribution: Boolean,
    subprojects: List<String> = listOf(),
    extraParameters: String = "",
    extraBuildSteps: BuildSteps.() -> Unit = {},
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(stage = stage, init = {
    this.name = name
    this.description = description
    this.id(id)
    val testTasks = getTestTaskName(testCoverage, subprojects)

    if (name.contains("(configuration-cache)")) {
        requirements {
            doesNotContain("teamcity.agent.name", "ec2")
            // US region agents have name "EC2-XXX"
            doesNotContain("teamcity.agent.name", "EC2")
        }
    }

    applyTestDefaults(
        model, this, testTasks,
        dependsOnQuickFeedbackLinux = !testCoverage.withoutDependencies && stage.stageName > StageName.PULL_REQUEST_FEEDBACK,
        os = testCoverage.os,
        buildJvm = testCoverage.buildJvm,
        arch = testCoverage.arch,
        extraParameters = (
            listOf(functionalTestExtraParameters(functionalTestTag, testCoverage.os, testCoverage.arch, testCoverage.testJvmVersion.major.toString(), testCoverage.vendor.name)) +
                (if (enableTestDistribution) "-DenableTestDistribution=%enableTestDistribution% -DtestDistributionPartitionSizeInSeconds=%testDistributionPartitionSizeInSeconds%" else "") +
                "-PflakyTests=${determineFlakyTestStrategy(stage)}" +
                extraParameters
            ).filter { it.isNotBlank() }.joinToString(separator = " "),
        timeout = testCoverage.testType.timeout,
        extraSteps = extraBuildSteps,
        preSteps = preBuildSteps
    )

    failureConditions {
        // JavaExecDebugIntegrationTest.debug session fails without debugger might cause JVM crash
        // Some soak tests produce OOM exceptions
        // There are also random worker crashes for some tests.
        javaCrash = false
    }
})

private fun determineFlakyTestStrategy(stage: Stage): String {
    val stageName = StageName.values().first { it.stageName == stage.stageName.stageName }
    // See gradlebuild.basics.FlakyTestStrategy
    return if (stageName < StageName.READY_FOR_RELEASE) "exclude" else "include"
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
