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

package model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import common.Os
import configurations.PerformanceTest
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import java.io.File
import java.util.LinkedList
import java.util.Locale

interface PerformanceTestBucketProvider {
    fun createPerformanceTestsFor(stage: Stage, performanceTestCoverage: PerformanceTestCoverage): List<PerformanceTest>
}

typealias OperatingSystemToTestProjectPerformanceTestDurations = Map<Os, Map<String, List<PerformanceTestDuration>>>

const val MAX_TEST_PROJECTS_PER_BUCKET = 10

data class PerformanceTestSpec(val performanceTestType: PerformanceTestType, val os: Os)

class StatisticsBasedPerformanceTestBucketProvider(private val model: CIBuildModel, performanceTestDurationsJson: File, performanceTestsCiJson: File) : PerformanceTestBucketProvider {
    private
    val objectMapper = ObjectMapper().registerKotlinModule()
    private
    val performanceTestConfigurations = readPerformanceTestConfigurations(performanceTestsCiJson)
    private
    val performanceTestDurations: OperatingSystemToTestProjectPerformanceTestDurations = readPerformanceTestDurations(performanceTestDurationsJson)

    override fun createPerformanceTestsFor(stage: Stage, performanceTestCoverage: PerformanceTestCoverage): List<PerformanceTest> {
        val performanceTestType = performanceTestCoverage.type
        val performanceTestSpec = PerformanceTestSpec(performanceTestType, performanceTestCoverage.os)
        val scenarios = determineScenariosFor(performanceTestSpec, performanceTestConfigurations)
        val testProjectToScenarioDurations = determineScenarioTestDurations(performanceTestSpec.os, performanceTestDurations)
        val testProjectScenarioDurationsFallback = determineScenarioTestDurations(Os.LINUX, performanceTestDurations)
        val buckets = splitBucketsByScenarios(
            scenarios,
            testProjectToScenarioDurations,
            testProjectScenarioDurationsFallback,
            performanceTestCoverage.numberOfBuckets
        )
        return buckets.mapIndexed { bucketIndex: Int, bucket: PerformanceTestBucket ->
            bucket.createPerformanceTestsFor(model, stage, performanceTestCoverage, bucketIndex)
        }
    }

    private fun readPerformanceTestDurations(performanceTestDurationsJson: File): OperatingSystemToTestProjectPerformanceTestDurations {
        val durations: List<Map<String, Any>> = objectMapper.readValue(performanceTestDurationsJson)
        val pairs =
            durations.flatMap { scenarioDurations ->
                val scenario = Scenario.fromTestId(scenarioDurations["scenario"] as String)
                (scenarioDurations["durations"] as List<Map<String, Any>>).flatMap { duration ->
                    val testProject = duration["testProject"] as String
                    duration.entries
                        .filter { (key, _) -> key != "testProject" }
                        .map { (osString, timeInMs) ->
                            val os = Os.valueOf(osString.uppercase(Locale.US))
                            val performanceTestDuration = PerformanceTestDuration(scenario, (timeInMs as Number).toInt())
                            os to (testProject to performanceTestDuration)
                        }
                }
            }
        return pairs
            .groupBy({ it.first }, { it.second })
            .mapValues { entry -> entry.value.groupBy({ it.first }, { it.second }) }
    }

    private fun readPerformanceTestConfigurations(performanceTestsCiJson: File): List<PerformanceTestConfiguration> {
        val performanceTestsCiJsonObj: Map<String, List<Map<String, Any>>> = objectMapper.readValue(performanceTestsCiJson)

        return performanceTestsCiJsonObj["performanceTests"]!!.map { scenarioObj ->
            val testId = scenarioObj["testId"] as String
            val groups =
                (scenarioObj["groups"] as List<Map<String, Any>>).map { groupObj ->
                    val testProject = groupObj["testProject"] as String
                    val coverage =
                        (groupObj["coverage"] as Map<String, List<String>>)
                            .map { (key, value) ->
                                val performanceTestType = PerformanceTestType.valueOf(key)
                                performanceTestType to
                                    value
                                        .map {
                                            Os.valueOf(it.uppercase(Locale.US))
                                        }.toSet()
                            }.toMap()
                    PerformanceTestGroup(testProject, coverage)
                }
            PerformanceTestConfiguration(testId, groups)
        }
    }
}

private
fun splitBucketsByScenarios(scenarios: List<PerformanceScenario>, testProjectToScenarioDurations: Map<String, List<PerformanceTestDuration>>, testProjectScenarioDurationsFallback: Map<String, List<PerformanceTestDuration>>, numberOfBuckets: Int): List<PerformanceTestBucket> {
    val testProjectDurations = scenarios
        .groupBy({ it.testProject }, { scenario ->
            listOf(testProjectToScenarioDurations, testProjectScenarioDurationsFallback)
                .mapNotNull { it.getOrDefault(scenario.testProject, emptyList()).firstOrNull { duration -> duration.scenario == scenario.scenario } }
                .firstOrNull() ?: PerformanceTestDuration(scenario.scenario, 1) // Fallback for new performance tests (don't use 0 to avoid division by zero)
        })
        .entries
        .map { TestProjectDuration(it.key, it.value) }
        .sortedBy { -it.totalTime }
    return splitIntoBuckets(
        LinkedList(testProjectDurations),
        TestProjectDuration::totalTime,
        { largeElement: TestProjectDuration, size: Int -> largeElement.split(size) },
        { list: List<TestProjectDuration> -> MultipleTestProjectBucket(list) },
        numberOfBuckets,
        MAX_TEST_PROJECTS_PER_BUCKET,
        { numEmptyBuckets -> (0 until numEmptyBuckets).map { EmptyTestProjectBucket(it) }.toList() },
        { tests1, tests2 -> tests1 != tests2 }
    )
}

fun determineScenarioTestDurations(os: Os, performanceTestDurations: OperatingSystemToTestProjectPerformanceTestDurations): Map<String, List<PerformanceTestDuration>> = performanceTestDurations.getOrDefault(os, emptyMap())

fun determineScenariosFor(performanceTestSpec: PerformanceTestSpec, performanceTestConfigurations: List<PerformanceTestConfiguration>): List<PerformanceScenario> {
    val performanceTestTypes = if (performanceTestSpec.performanceTestType in setOf(PerformanceTestType.historical, PerformanceTestType.flakinessDetection)) {
        listOf(PerformanceTestType.per_commit, PerformanceTestType.per_day)
    } else {
        listOf(performanceTestSpec.performanceTestType)
    }
    return performanceTestConfigurations.flatMap { configuration ->
        configuration.groups
            .filter { group ->
                performanceTestTypes.any { type ->
                    group.performanceTestTypes[type]?.contains(performanceTestSpec.os) == true
                }
            }
            .map { PerformanceScenario(Scenario.fromTestId(configuration.testId), it.testProject) }
    }
}

data class PerformanceTestDuration(val scenario: Scenario, val durationInMs: Int) {
    fun toCsvLine() = "${scenario.className};${scenario.scenario}"
}

data class PerformanceScenario(val scenario: Scenario, val testProject: String)

interface PerformanceTestBucket {
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest

    fun getName(testCoverage: TestCoverage): String = throw UnsupportedOperationException()
}

data class TestProjectDuration(val testProject: String, val scenarioDurations: List<PerformanceTestDuration>) {
    val totalTime: Int = scenarioDurations.sumOf { it.durationInMs }

    fun split(expectedBucketNumber: Int): List<PerformanceTestBucket> {
        return if (expectedBucketNumber == 1 || scenarioDurations.size == 1) {
            listOf(SingleTestProjectBucket(testProject, scenarioDurations.map { it.scenario }))
        } else {
            val list = LinkedList(scenarioDurations.sortedBy { -it.durationInMs })
            val toIntFunction = PerformanceTestDuration::durationInMs
            val largeElementSplitFunction: (PerformanceTestDuration, Int) -> List<List<PerformanceTestDuration>> = { performanceTestDuration: PerformanceTestDuration, _: Int -> listOf(listOf(performanceTestDuration)) }
            val smallElementAggregateFunction: (List<PerformanceTestDuration>) -> List<PerformanceTestDuration> = { it }

            val buckets: List<List<PerformanceTestDuration>> = splitIntoBuckets(list, toIntFunction, largeElementSplitFunction, smallElementAggregateFunction, expectedBucketNumber, Integer.MAX_VALUE, { listOf() })
                .filter { it.isNotEmpty() }

            buckets.mapIndexed { index: Int, classesInBucket: List<PerformanceTestDuration> ->
                TestProjectSplitBucket(testProject, index + 1, classesInBucket)
            }
        }
    }

    override
    fun toString(): String {
        return "TestProjectTime(testProject=$testProject, totalTime=$totalTime, scenarios = ${scenarioDurations.map { it.scenario } }"
    }
}

data class Scenario(val className: String, val scenario: String) {
    companion object {
        fun fromTestId(testId: String): Scenario {
            val dotBeforeScenarioName = testId.lastIndexOf('.')
            return Scenario(testId.substring(0, dotBeforeScenarioName), testId.substring(dotBeforeScenarioName + 1))
        }
    }

    override
    fun toString(): String =
        "$className.$scenario"
}

class SingleTestProjectBucket(val testProject: String, val scenarios: List<Scenario>) : PerformanceTestBucket {
    override
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest = createPerformanceTest(
        model,
        performanceTestCoverage,
        stage,
        bucketIndex,
        "Performance tests for $testProject",
        mapOf(testProject to scenarios)
    )
}

class MultipleTestProjectBucket(private val projectDurations: List<TestProjectDuration>) : PerformanceTestBucket {
    override
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest = createPerformanceTest(
        model,
        performanceTestCoverage,
        stage,
        bucketIndex,
        "Performance tests for ${projectDurations.joinToString(", ") { it.testProject }}",
        projectDurationsToScenariosPerTestProject(projectDurations)
    )
}

private
fun projectDurationsToScenariosPerTestProject(projectDurations: List<TestProjectDuration>): Map<String, List<Scenario>> {
    val durationsPerTestProject = projectDurations.groupBy({ it.testProject }, { it.scenarioDurations })
    durationsPerTestProject.forEach { (key, value) -> if (value.size != 1) throw IllegalArgumentException("More than one scenario split for test project $key: $projectDurations") }
    return durationsPerTestProject
        .mapValues { (_, durations) -> durations.flatten().map { it.scenario } }
}

class EmptyTestProjectBucket(private val index: Int) : PerformanceTestBucket {
    override
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest = createPerformanceTest(
        model,
        performanceTestCoverage,
        stage,
        bucketIndex,
        "Empty Performance Test bucket $index",
        mapOf()
    )
}

class TestProjectSplitBucket(val testProject: String, private val number: Int, val scenarios: List<PerformanceTestDuration>) : PerformanceTestBucket {
    override
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest = createPerformanceTest(
        model,
        performanceTestCoverage,
        stage,
        bucketIndex,
        "Performance test for $testProject (bucket $number)",
        mapOf(testProject to scenarios.map { it.scenario })
    )
}

private
fun createPerformanceTest(model: CIBuildModel, performanceTestCoverage: PerformanceTestCoverage, stage: Stage, bucketIndex: Int, description: String, tests: Map<String, List<Scenario>>): PerformanceTest {
    val descriptionWithMaybeBucketIndex = if (performanceTestCoverage.type == PerformanceTestType.flakinessDetection)
        "$description - index $bucketIndex"
    else
        description
    return PerformanceTest(
        model,
        stage,
        performanceTestCoverage,
        description = descriptionWithMaybeBucketIndex,
        performanceSubProject = "performance",
        testProjects = tests.keys.toList(),
        bucketIndex = bucketIndex,
        extraParameters = "-PincludePerformanceTestScenarios=true"
    ) {
        tests.forEach { (testProject, scenarios) ->
            prepareScenariosStep(testProject, scenarios, performanceTestCoverage.os)()
        }
    }
}

private
fun prepareScenariosStep(testProject: String, scenarios: List<Scenario>, os: Os): BuildSteps.() -> Unit {
    if (scenarios.isEmpty()) {
        throw IllegalArgumentException("Scenarios list must not be empty for $testProject")
    }
    val csvLines = scenarios.map { "${it.className};${it.scenario}" }
    val action = "include"
    val fileNamePostfix = "$testProject-performance-scenarios.csv"
    val performanceTestSplitDirectoryName = "performance-test-splits"
    val unixScript = """
mkdir -p $performanceTestSplitDirectoryName
rm -rf $performanceTestSplitDirectoryName/*-$fileNamePostfix
cat > $performanceTestSplitDirectoryName/$action-$fileNamePostfix << EOL
${csvLines.joinToString("\n")}
EOL

echo "Performance tests to be ${action}d in this build"
cat $performanceTestSplitDirectoryName/$action-$fileNamePostfix
"""

    val linesWithEcho = csvLines.joinToString("\n") { """echo $it >> $performanceTestSplitDirectoryName\$action-$fileNamePostfix""" }

    val windowsScript = """
mkdir $performanceTestSplitDirectoryName
del /f /q $performanceTestSplitDirectoryName\include-$fileNamePostfix
del /f /q $performanceTestSplitDirectoryName\exclude-$fileNamePostfix
$linesWithEcho

echo Performance tests to be ${action}d in this build
type $performanceTestSplitDirectoryName\$action-$fileNamePostfix
"""

    return {
        script {
            name = "PREPARE_TEST_CLASSES"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = if (os == Os.WINDOWS) windowsScript else unixScript
        }
    }
}

data class PerformanceTestGroup(val testProject: String, val performanceTestTypes: Map<PerformanceTestType, Set<Os>>)

data class PerformanceTestConfiguration(val testId: String, val groups: List<PerformanceTestGroup>)
