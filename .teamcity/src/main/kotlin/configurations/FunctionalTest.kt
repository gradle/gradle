package configurations

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import common.functionalTestExtraParameters
import common.getBuildScanCustomValueParam
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import model.CIBuildModel
import model.Stage
import model.StageName
import model.TestCoverage
import model.TestType

const val FUNCTIONAL_TEST_TAG = "FunctionalTest"

sealed class ParallelizationMethod {
    open val extraBuildParameters: String
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        get() = ""

    val name: String = this::class.simpleName!!

    object None : ParallelizationMethod()

    object TestDistribution : ParallelizationMethod() {
        override val extraBuildParameters: String =
            "-DenableTestDistribution=%enableTestDistribution% " +
                "-DtestDistributionPartitionSizeInSeconds=%testDistributionPartitionSizeInSeconds%"
    }

    object TestDistributionAlpine : ParallelizationMethod() {
        override val extraBuildParameters: String =
            listOf(
                "-DenableTestDistribution=true",
                "-DtestDistributionPartitionSizeInSeconds=%testDistributionPartitionSizeInSeconds%",
                "-PtestDistributionDogfoodingTag=alpine",
                "-PmaxTestDistributionLocalExecutors=0",
            ).joinToString(" ")
    }

    class TeamCityParallelTests(
        val numberOfBatches: Int,
    ) : ParallelizationMethod()

    companion object {
        private val objectMapper = ObjectMapper()

        fun fromJson(jsonObject: Map<String, Any>): ParallelizationMethod {
            val methodJsonNode =
                (jsonObject["parallelizationMethod"] as? Map<*, *>)?.let { objectMapper.valueToTree<JsonNode>(it) }
                    ?: return None

            return when (methodJsonNode.get("name")?.asText()) {
                null -> None
                None::class.simpleName -> None
                TestDistribution::class.simpleName -> TestDistribution
                TestDistributionAlpine::class.simpleName -> TestDistributionAlpine
                TeamCityParallelTests::class.simpleName ->
                    TeamCityParallelTests(
                        methodJsonNode.get("numberOfBatches").asInt(),
                    )

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
    extraBuildSteps: BuildSteps.() -> Unit = {},
    preBuildSteps: BuildSteps.() -> Unit = {},
) : OsAwareBaseGradleBuildType(os = testCoverage.os, stage = stage, init = {
        this.name = name
        this.description = description
        this.id(id)
        val testTasks = getTestTaskName(testCoverage, subprojects)

        val assembledExtraParameters =
            mutableListOf(
                stage.getBuildScanCustomValueParam(testCoverage),
                functionalTestExtraParameters(
                    listOf(FUNCTIONAL_TEST_TAG),
                    testCoverage.os,
                    testCoverage.arch,
                    testCoverage.testJvmVersion.major.toString(),
                    testCoverage.vendor.name.lowercase(),
                ),
                "-PflakyTests=${determineFlakyTestStrategy(stage)}",
                extraParameters,
                parallelizationMethod.extraBuildParameters,
            ).filter { it.isNotBlank() }.joinToString(separator = " ")

        if (parallelizationMethod is ParallelizationMethod.TeamCityParallelTests) {
            tcParallelTests(parallelizationMethod.numberOfBatches)
        }

        features {
            perfmon {
            }
        }

        applyTestDefaults(
            model,
            this,
            testTasks,
            os = testCoverage.os,
            buildJvm = testCoverage.buildJvm,
            arch = testCoverage.arch,
            extraParameters = assembledExtraParameters,
            timeout = testCoverage.testType.timeout,
            maxParallelForks = testCoverage.testType.maxParallelForks.toString(),
            extraSteps = extraBuildSteps,
            preSteps = preBuildSteps,
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

fun getTestTaskName(
    testCoverage: TestCoverage,
    subprojects: List<String>,
): String {
    val testTaskName =
        if (testCoverage.testType == TestType.ISOLATED_PROJECTS) {
            "isolatedProjectsIntegTest"
        } else {
            "${testCoverage.testType.asCamelCase()}Test"
        }
    return when {
        subprojects.isEmpty() -> {
            testTaskName
        }

        else -> {
            subprojects.joinToString(" ") { "$it:$testTaskName" }
        }
    }
}
