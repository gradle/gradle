package configurations

import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.annotation.JSONField
import common.functionalTestExtraParameters
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import model.CIBuildModel
import model.Stage
import model.StageName
import model.TestCoverage
import model.TestType

const val functionalTestTag = "FunctionalTest"

sealed class ParallelizationMethod {
    open val extraBuildParameters: String
        @JSONField(serialize = false)
        get() = ""

    val name: String = this::class.simpleName!!

    object None : ParallelizationMethod()
    object TestDistribution : ParallelizationMethod() {
        override val extraBuildParameters: String = "-DenableTestDistribution=%enableTestDistribution% -DtestDistributionPartitionSizeInSeconds=%testDistributionPartitionSizeInSeconds%"
    }

    class TeamCityParallelTests(val numberOfBatches: Int) : ParallelizationMethod()

    companion object {
        fun fromJson(jsonObject: JSONObject): ParallelizationMethod {
            val methodJsonObject = jsonObject.getJSONObject("parallelizationMethod") ?: return None
            return when (methodJsonObject.getString("name")) {
                null -> None
                TestDistribution::class.simpleName -> TestDistribution
                TeamCityParallelTests::class.simpleName -> TeamCityParallelTests(methodJsonObject.getIntValue("numberOfBatches"))
                else -> throw IllegalArgumentException("Unknown parallelization method")
            }
        }
    }
}

class FunctionalTest(
    model: CIBuildModel,
    id: String,
    name: String,
    description: String,
    val testCoverage: TestCoverage,
    stage: Stage,
    parallelizationMethod: ParallelizationMethod = ParallelizationMethod.None,
    subprojects: List<String> = listOf(),
    extraParameters: String = "",
    maxParallelForks: String = "%maxParallelForks%",
    extraBuildSteps: BuildSteps.() -> Unit = {},
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(stage = stage, init = {
    this.name = name
    this.description = description
    this.id(id)
    val testTasks = getTestTaskName(testCoverage, subprojects)

    val assembledExtraParameters = mutableListOf(
        functionalTestExtraParameters(functionalTestTag, testCoverage.os, testCoverage.arch, testCoverage.testJvmVersion.major.toString(), testCoverage.vendor.name),
        "-PflakyTests=${determineFlakyTestStrategy(stage)}",
        extraParameters,
        parallelizationMethod.extraBuildParameters
    ).filter { it.isNotBlank() }.joinToString(separator = " ")

    if (parallelizationMethod is ParallelizationMethod.TeamCityParallelTests) {
        tcParallelTests(parallelizationMethod.numberOfBatches)
    }

    features {
        perfmon {
        }
    }

    applyTestDefaults(
        model, this, testTasks,
        dependsOnQuickFeedbackLinux = !testCoverage.withoutDependencies && stage.stageName > StageName.PULL_REQUEST_FEEDBACK,
        os = testCoverage.os,
        buildJvm = testCoverage.buildJvm,
        arch = testCoverage.arch,
        extraParameters = assembledExtraParameters,
        timeout = testCoverage.testType.timeout,
        maxParallelForks = testCoverage.testType.maxParallelForks.toString(),
        extraSteps = extraBuildSteps,
        preSteps = preBuildSteps
    )

    failureConditions {
        // JavaExecDebugIntegrationTest.debug session fails without debugger might cause JVM crash
        // Some soak tests produce OOM exceptions
        // There are also random worker crashes for some tests.
        // We have test-retry to handle the crash in tests
        javaCrash = false
    }
})

private fun determineFlakyTestStrategy(stage: Stage): String {
    val stageName = StageName.values().first { it.stageName == stage.stageName.stageName }
    // See gradlebuild.basics.FlakyTestStrategy
    return if (stageName < StageName.READY_FOR_RELEASE) "exclude" else "include"
}

fun getTestTaskName(testCoverage: TestCoverage, subprojects: List<String>): String {
    val testTaskName =
        if (testCoverage.testType == TestType.isolatedProjects) "isolatedProjectsIntegTest" else "${testCoverage.testType.name}Test"
    return when {
        subprojects.isEmpty() -> {
            testTaskName
        }

        else -> {
            subprojects.joinToString(" ") { "$it:$testTaskName" }
        }
    }
}
