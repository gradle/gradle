/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package Gradle_Check.model

import Gradle_Check.configurations.PerformanceTest
import common.Os
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage
import model.StageNames
import model.TestCoverage
import java.io.File
import java.util.LinkedList

interface PerformanceTestBucketProvider {
    fun createPerformanceTestsFor(stage: Stage, os: Os): List<PerformanceTest>
}

typealias OperatingSystemToTestProjectPerformanceTestTimes = Map<String, Map<String, List<PerformanceTestTime>>>

const val PERFORMANCE_TEST_BUCKET_NUMBER = 40

data class PerformanceTestCoverage(val stageId: String, val os: Os) {
    fun asConfigurationId(model: CIBuildModel, bucket: String = "") = "${model.projectPrefix}$stageId${os.asName()}PerformanceTest$bucket"
    fun asName(): String =
        "Performance tests in $stageId - ${os.asName()}"
}

private
val upToDateParallel = Scenario("org.gradle.performance.regression.java.JavaUpToDatePerformanceTest", "up-to-date assemble (parallel true)")

private
val upToDateNonParallel = Scenario("org.gradle.performance.regression.java.JavaUpToDatePerformanceTest", "up-to-date assemble (parallel false)")

class StatisticsBasedPerformanceTestBucketProvider(private val model: CIBuildModel, performanceTestTimeDataCsv: File) : PerformanceTestBucketProvider {
    private val buckets: Map<PerformanceTestCoverage, List<PerformanceTestBucket>> = buildBuckets(performanceTestTimeDataCsv, model)

    override fun createPerformanceTestsFor(stage: Stage, os: Os): List<PerformanceTest> {
        val performanceTestCoverage = PerformanceTestCoverage(stage.id, os)
        return buckets.getValue(performanceTestCoverage).mapIndexed { bucketIndex: Int, bucket: PerformanceTestBucket ->
            bucket.createPerformanceTestsFor(model, stage, performanceTestCoverage, bucketIndex)
        }
    }

    private
    fun buildBuckets(performanceTestTimeDataCsv: File, model: CIBuildModel): Map<PerformanceTestCoverage, List<PerformanceTestBucket>> {
        val performanceTestTimes: OperatingSystemToTestProjectPerformanceTestTimes = mapOf(
            "Linux" to mapOf(
                "largeJavaMultiProject" to listOf(
                    PerformanceTestTime(upToDateParallel, 476000),
                    PerformanceTestTime(upToDateNonParallel, 700000)
                ),
                "largeMonolithicJavaProject" to listOf(
                    PerformanceTestTime(upToDateNonParallel, 308000)
                )
            )
        )

        val result = mutableMapOf<PerformanceTestCoverage, List<PerformanceTestBucket>>()
        for (stage in model.stages) {
            val performanceTestCoverage = PerformanceTestCoverage(stage.id, Os.LINUX)
            result[performanceTestCoverage] = splitBucketsByScenarios(performanceTestCoverage, performanceTestTimes)
        }
        return result
    }
}

private
fun splitBucketsByScenarios(performanceTestCoverage: PerformanceTestCoverage, performanceTestTimes: OperatingSystemToTestProjectPerformanceTestTimes): List<PerformanceTestBucket> {
    val scenarios = determineScenariosFor(performanceTestCoverage)

    val testProjectToScenarioTimes: Map<String, List<PerformanceTestTime>> = determineScenarioTestTimes(performanceTestCoverage, performanceTestTimes)

    val testProjectTimes = scenarios
        .groupBy({ it.testProject }, { testProjectToScenarioTimes.getValue(it.testProject).first { times -> times.scenario == it.scenario } })
        .entries
        .map { TestProjectTime(it.key, it.value) }
        .sortedBy { -it.totalTime }
    if (testProjectTimes.isEmpty()) {
        return emptyList()
    }
    return split(
        LinkedList(testProjectTimes),
        TestProjectTime::totalTime,
        { largeElement: TestProjectTime, size: Int -> largeElement.split(size) },
        { list: List<TestProjectTime> -> MultipleTestProjectBucket(list) },
        PERFORMANCE_TEST_BUCKET_NUMBER,
        MAX_PROJECT_NUMBER_IN_BUCKET,
        { numEmptyBuckets -> (0 until numEmptyBuckets).map { EmptyTestProjectBucket(it) }.toList() }
    )
}

fun determineScenarioTestTimes(performanceTestCoverage: PerformanceTestCoverage, performanceTestTimes: OperatingSystemToTestProjectPerformanceTestTimes): Map<String, List<PerformanceTestTime>> = performanceTestTimes.getValue(performanceTestCoverage.os.asName())

fun determineScenariosFor(performanceTestCoverage: PerformanceTestCoverage): List<PerformanceScenario> = when (performanceTestCoverage) {
    PerformanceTestCoverage(StageNames.EXPERIMENTAL_PERFORMANCE.id, Os.LINUX) -> listOf(
        PerformanceScenario(upToDateParallel, "largeJavaMultiProject"),
        PerformanceScenario(upToDateNonParallel, "largeJavaMultiProject"),
        PerformanceScenario(upToDateNonParallel, "largeMonolithicJavaProject")
    )
    else -> listOf()
}

class PerformanceTestTime(val scenario: Scenario, val buildTimeMs: Int) {
    fun toCsvLine() = "${scenario.className};${scenario.scenario}"
}

data class PerformanceScenario(val scenario: Scenario, val testProject: String)

interface PerformanceTestBucket {
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest

    fun getUuid(model: CIBuildModel, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): String = performanceTestCoverage.asConfigurationId(model, "bucket${bucketIndex + 1}")

    fun getName(testCoverage: TestCoverage): String = throw UnsupportedOperationException()

    fun getDescription(testCoverage: TestCoverage): String = throw UnsupportedOperationException()
}

class TestProjectTime(val testProject: String, val scenarioTimes: List<PerformanceTestTime>) {
    val totalTime: Int = scenarioTimes.sumBy { it.buildTimeMs }

    fun split(expectedBucketNumber: Int): List<PerformanceTestBucket> {
        return if (expectedBucketNumber == 1 || scenarioTimes.size == 1) {
            listOf(SingleTestProjectBucket(testProject, scenarioTimes.map { it.scenario }))
        } else {
            val list = LinkedList(scenarioTimes.sortedBy { -it.buildTimeMs })
            val toIntFunction = PerformanceTestTime::buildTimeMs
            val largeElementSplitFunction: (PerformanceTestTime, Int) -> List<List<PerformanceTestTime>> = { performanceTestTime: PerformanceTestTime, number: Int -> listOf(listOf(performanceTestTime)) }
            val smallElementAggregateFunction: (List<PerformanceTestTime>) -> List<PerformanceTestTime> = { it }

            val buckets: List<List<PerformanceTestTime>> = split(list, toIntFunction, largeElementSplitFunction, smallElementAggregateFunction, expectedBucketNumber, Integer.MAX_VALUE, { listOf() })

            buckets.mapIndexed { index: Int, classesInBucket: List<PerformanceTestTime> ->
                TestProjectSplitBucket(testProject, index + 1, classesInBucket)
            }
        }
    }

    override fun toString(): String {
        return "TestProjectScenarioTime(testProject=$testProject, totalTime=$totalTime)"
    }
}

data class Scenario(val className: String, val scenario: String)

class SingleTestProjectBucket(val testProject: String, val scenarios: List<Scenario>) : PerformanceTestBucket {
    override fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest {
        val uuid = getUuid(model, performanceTestCoverage, bucketIndex)
        return PerformanceTest(
            model,
            PerformanceTestType.test,
            stage,
            uuid,
            "Performance tests for $testProject",
            "performance",
            listOf(testProject),
            performanceTestCoverage.os,
            extraParameters = " -PincludePerformanceTestScenarios=true",
            preBuildSteps = prepareScenariosStep(testProject, scenarios, performanceTestCoverage.os)
        )
    }
}

class MultipleTestProjectBucket(val testProjects: List<TestProjectTime>) : PerformanceTestBucket {
    override fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest {
        val uuid = getUuid(model, performanceTestCoverage, bucketIndex)
        return PerformanceTest(
            model,
            PerformanceTestType.test,
            stage,
            uuid,
            "Performance tests for ${testProjects.joinToString(", ")}",
            "performance",
            testProjects.map { it.testProject }.distinct(),
            performanceTestCoverage.os,
            extraParameters = " -PincludePerformanceTestScenarios=true",
            preBuildSteps = {
                testProjects.forEach { testProject ->
                    prepareScenariosStep(testProject.testProject, testProject.scenarioTimes.map(PerformanceTestTime::scenario), performanceTestCoverage.os)()
                }
            }
        )
    }
}

class EmptyTestProjectBucket(private val index: Int) : PerformanceTestBucket {
    override fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest {
        val uuid = getUuid(model, performanceTestCoverage, bucketIndex)
        return PerformanceTest(
            model,
            PerformanceTestType.test,
            stage,
            uuid,
            "EmptyPerformanceTestsFor$index",
            "performance",
            listOf(),
            performanceTestCoverage.os
        )
    }
}

class TestProjectSplitBucket(val testProject: String, val number: Int, val scenarios: List<PerformanceTestTime>) : PerformanceTestBucket {
    override fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest {
        val uuid = getUuid(model, performanceTestCoverage, bucketIndex)
        return PerformanceTest(
            model,
            PerformanceTestType.test,
            stage,
            uuid,
            "Performance test for $testProject (bucket $number)",
            "performance",
            listOf(testProject),
            performanceTestCoverage.os,
            extraParameters = " -PincludePerformanceTestScenarios=true",
            preBuildSteps = prepareScenariosStep(testProject, scenarios.map(PerformanceTestTime::scenario), performanceTestCoverage.os)
        )
    }
}

private fun prepareScenariosStep(testProject: String, scenarios: List<Scenario>, os: Os): BuildSteps.() -> Unit {
    val csvLines = scenarios.map { "${it.className};${it.scenario}" }
    val action = "include"
    val fileNamePostfix = "$testProject-performance-scenarios.csv"
    val unixScript = """
mkdir -p build
rm -rf build/*-$fileNamePostfix
cat > build/$action-$fileNamePostfix << EOL
${csvLines.joinToString("\n")}
EOL

echo "Performance tests to be ${action}d in this build"
cat build/$action-$fileNamePostfix
"""

    val linesWithEcho = csvLines.joinToString("\n") { "echo $it" }

    val windowsScript = """
mkdir build
del /f /q build\include-$fileNamePostfix
del /f /q build\exclude-$fileNamePostfix
(
$linesWithEcho
) > build\$action-$fileNamePostfix

echo "Performance tests to be ${action}d in this build"
type build\$action-$fileNamePostfix
"""

    return {
        script {
            name = "PREPARE_TEST_CLASSES"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = if (os == Os.WINDOWS) windowsScript else unixScript
        }
    }
}
