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
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import common.Os
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage
import model.TestCoverage
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.LinkedList
import java.util.Locale

interface PerformanceTestBucketProvider {
    fun createPerformanceTestsFor(stage: Stage, performanceTestCoverage: PerformanceTestCoverage): List<PerformanceTest>
}

typealias OperatingSystemToTestProjectPerformanceTestTimes = Map<Os, Map<String, List<PerformanceTestTime>>>

const val MAX_TEST_PROJECTS_PER_BUCKET = 10

data class PerformanceTestCoverage(val performanceTestType: PerformanceTestType, val os: Os) {
    fun asConfigurationId(model: CIBuildModel, stage: Stage, bucket: String = "") = "${model.projectPrefix}PerformanceTest${performanceTestType.name.capitalize()}${os.asName()}$bucket"
    fun asName(): String =
        "${performanceTestType.displayName} - ${os.asName()}"

    fun channel(branch: String = "%teamcity.build.branch%") =
        "${performanceTestType.channel}${if (os == Os.LINUX) "" else "-${os.name.toLowerCase(Locale.US)}"}-$branch"
}

class StatisticsBasedPerformanceTestBucketProvider(private val model: CIBuildModel, performanceTestTimeDataCsv: File, performanceTestsCiJson: File) : PerformanceTestBucketProvider {
    private val buckets: Map<PerformanceTestCoverage, List<PerformanceTestBucket>> = buildBuckets(performanceTestTimeDataCsv, performanceTestsCiJson)

    override fun createPerformanceTestsFor(stage: Stage, performanceTestCoverage: PerformanceTestCoverage): List<PerformanceTest> {
        return buckets.getValue(performanceTestCoverage).mapIndexed { bucketIndex: Int, bucket: PerformanceTestBucket ->
            bucket.createPerformanceTestsFor(model, stage, performanceTestCoverage, bucketIndex)
        }
    }

    private
    fun buildBuckets(performanceTestTimeDataCsv: File, performanceTestsCiJson: File): Map<PerformanceTestCoverage, List<PerformanceTestBucket>> {
        val performanceTestConfigurations = readPerformanceTestConfigurations(performanceTestsCiJson)

        val performanceTestTimes: OperatingSystemToTestProjectPerformanceTestTimes = readPerformanceTestTimes(performanceTestTimeDataCsv)

        val result = mutableMapOf<PerformanceTestCoverage, List<PerformanceTestBucket>>()
        for (performanceTestType in PerformanceTestType.values()) {
            val performanceTestCoverage = PerformanceTestCoverage(performanceTestType, Os.LINUX)
            val scenarios = determineScenariosFor(performanceTestCoverage, performanceTestConfigurations)
            val testProjectToScenarioTimes = determineScenarioTestTimes(performanceTestCoverage.os, performanceTestTimes)
            val repetitions = if (performanceTestType == PerformanceTestType.flakinessDetection) 3 else 1
            result[performanceTestCoverage] = splitBucketsByScenarios(scenarios, testProjectToScenarioTimes, repetitions, performanceTestType.numberOfBuckets)
        }
        return result
    }

    private
    fun readPerformanceTestTimes(performanceTestTimeDataCsv: File): OperatingSystemToTestProjectPerformanceTestTimes {
        val pairs: List<Pair<Os, Pair<String, PerformanceTestTime>>> = performanceTestTimeDataCsv.readLines(StandardCharsets.UTF_8)
            .map { line ->
                val (className, scenarioId, testProject, os, durationInMs) = line.split(';')
                val scenario = Scenario(className, scenarioId)
                val performanceTestTime = PerformanceTestTime(scenario, durationInMs.toInt())
                Os.valueOf(os.toUpperCase(Locale.US)) to (testProject to performanceTestTime)
            }
        return pairs.groupBy({ it.first }, { it.second })
            .mapValues { entry -> entry.value.groupBy({ it.first }, { it.second }) }
    }

    private
    fun readPerformanceTestConfigurations(performanceTestsCiJson: File): List<PerformanceTestConfiguration> {
        val performanceTestsCiJsonObj = JSON.parseObject(performanceTestsCiJson.readText(StandardCharsets.UTF_8)) as JSONObject

        return (performanceTestsCiJsonObj["performanceTests"] as JSONArray).map {
            val scenarioObj = it as JSONObject
            val testId = scenarioObj["testId"] as String
            val groups = (scenarioObj["groups"] as JSONArray).map {
                val groupObj = it as JSONObject
                val testProject = groupObj["testProject"] as String
                val coverage = (groupObj["coverage"] as JSONObject).map { entry ->
                    val performanceTestType = PerformanceTestType.valueOf(entry.key as String)
                    performanceTestType to (entry.value as JSONArray).map {
                        Os.valueOf((it as String).toUpperCase(Locale.US))
                    }.toSet()
                }.toMap()
                PerformanceTestGroup(testProject, coverage)
            }
            PerformanceTestConfiguration(testId, groups)
        }
    }
}

private
fun splitBucketsByScenarios(scenarios: List<PerformanceScenario>, testProjectToScenarioTimes: Map<String, List<PerformanceTestTime>>, scenarioRepetitions: Int, numberOfBuckets: Int): List<PerformanceTestBucket> {

    val testProjectTimes = scenarios
        .groupBy({ it.testProject }, { testProjectToScenarioTimes.getValue(it.testProject).first { times -> times.scenario == it.scenario } })
        .entries
        .map { TestProjectTime(it.key, it.value) }
        .flatMap { testProjectTime -> generateSequence { testProjectTime }.take(scenarioRepetitions).toList() }
        .sortedBy { -it.totalTime }
    return splitIntoBuckets(
        LinkedList(testProjectTimes),
        TestProjectTime::totalTime,
        { largeElement: TestProjectTime, size: Int -> largeElement.split(size) },
        { list: List<TestProjectTime> -> MultipleTestProjectBucket(list) },
        numberOfBuckets,
        MAX_TEST_PROJECTS_PER_BUCKET,
        { numEmptyBuckets -> (0 until numEmptyBuckets).map { EmptyTestProjectBucket(it) }.toList() },
        { tests1, tests2 -> tests1 != tests2 }
    )
}

fun determineScenarioTestTimes(os: Os, performanceTestTimes: OperatingSystemToTestProjectPerformanceTestTimes): Map<String, List<PerformanceTestTime>> = performanceTestTimes.getValue(os)

fun determineScenariosFor(performanceTestCoverage: PerformanceTestCoverage, performanceTestConfigurations: List<PerformanceTestConfiguration>): List<PerformanceScenario> {
    val performanceTestType = if (performanceTestCoverage.performanceTestType in setOf(PerformanceTestType.historical, PerformanceTestType.flakinessDetection)) {
        PerformanceTestType.test
    } else {
        performanceTestCoverage.performanceTestType
    }
    return performanceTestConfigurations.flatMap { configuration ->
        configuration.groups
            .filter { it.performanceTestTypes[performanceTestType]?.contains(performanceTestCoverage.os) == true }
            .map { PerformanceScenario(Scenario.fromTestId(configuration.testId), it.testProject) }
    }
}

data class PerformanceTestTime(val scenario: Scenario, val buildTimeMs: Int) {
    fun toCsvLine() = "${scenario.className};${scenario.scenario}"
}

data class PerformanceScenario(val scenario: Scenario, val testProject: String)

interface PerformanceTestBucket {
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest

    fun getName(testCoverage: TestCoverage): String = throw UnsupportedOperationException()
}

data class TestProjectTime(val testProject: String, val scenarioTimes: List<PerformanceTestTime>) {
    val totalTime: Int = scenarioTimes.sumBy { it.buildTimeMs }

    fun split(expectedBucketNumber: Int): List<PerformanceTestBucket> {
        return if (expectedBucketNumber == 1 || scenarioTimes.size == 1) {
            listOf(SingleTestProjectBucket(testProject, scenarioTimes.map { it.scenario }))
        } else {
            val list = LinkedList(scenarioTimes.sortedBy { -it.buildTimeMs })
            val toIntFunction = PerformanceTestTime::buildTimeMs
            val largeElementSplitFunction: (PerformanceTestTime, Int) -> List<List<PerformanceTestTime>> = { performanceTestTime: PerformanceTestTime, _: Int -> listOf(listOf(performanceTestTime)) }
            val smallElementAggregateFunction: (List<PerformanceTestTime>) -> List<PerformanceTestTime> = { it }

            val buckets: List<List<PerformanceTestTime>> = splitIntoBuckets(list, toIntFunction, largeElementSplitFunction, smallElementAggregateFunction, expectedBucketNumber, Integer.MAX_VALUE, { listOf() })
                .filter { it.isNotEmpty() }

            buckets.mapIndexed { index: Int, classesInBucket: List<PerformanceTestTime> ->
                TestProjectSplitBucket(testProject, index + 1, classesInBucket)
            }
        }
    }

    override
    fun toString(): String {
        return "TestProjectScenarioTime(testProject=$testProject, totalTime=$totalTime)"
    }
}

data class Scenario(val className: String, val scenario: String) {
    companion object {
        fun fromTestId(testId: String): Scenario {
            val dotBeforeScenarioName = testId.lastIndexOf('.')
            return Scenario(testId.substring(0, dotBeforeScenarioName), testId.substring(dotBeforeScenarioName + 1))
        }
    }
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

class MultipleTestProjectBucket(private val projectTimes: List<TestProjectTime>) : PerformanceTestBucket {
    override
    fun createPerformanceTestsFor(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, bucketIndex: Int): PerformanceTest = createPerformanceTest(
            model,
            performanceTestCoverage,
            stage,
            bucketIndex,
            "Performance tests for ${projectTimes.joinToString(", ") { it.testProject }}",
            projectTimesToScenariosPerTestProject(projectTimes)
        )
}

private
fun projectTimesToScenariosPerTestProject(projectTimes: List<TestProjectTime>): Map<String, List<Scenario>> {
    val timesPerTestProject = projectTimes.groupBy({ it.testProject }, { it.scenarioTimes })
    timesPerTestProject.forEach { (key, value) -> if (value.size != 1) throw IllegalArgumentException("More than on scenario split for test project $key: $projectTimes") }
    return timesPerTestProject
        .mapValues { (_, times) -> times.flatten().map { it.scenario } }
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

class TestProjectSplitBucket(val testProject: String, private val number: Int, val scenarios: List<PerformanceTestTime>) : PerformanceTestBucket {
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
    val descriptionWithMaybeBucketIndex = if (performanceTestCoverage.performanceTestType == PerformanceTestType.flakinessDetection)
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
        extraParameters = " -PincludePerformanceTestScenarios=true"
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

    val linesWithEcho = csvLines.joinToString("\n") { "echo $it" }

    val windowsScript = """
mkdir $performanceTestSplitDirectoryName
del /f /q $performanceTestSplitDirectoryName\include-$fileNamePostfix
del /f /q $performanceTestSplitDirectoryName\exclude-$fileNamePostfix
(
$linesWithEcho
) > $performanceTestSplitDirectoryName\$action-$fileNamePostfix

echo "Performance tests to be ${action}d in this build"
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
