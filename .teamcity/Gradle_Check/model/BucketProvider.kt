package Gradle_Check.model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import common.Os
import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import model.BuildTypeBucket
import model.CIBuildModel
import model.GradleSubproject
import model.Stage
import model.TestCoverage
import model.TestType
import java.io.File

const val BUCKET_NUMBER = 40

typealias BuildProjectToSubprojectTestClassTimes = Map<String, Map<String, List<TestClassTime>>>

interface GradleBuildBucketProvider {
    fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest>

    fun createDeferredFunctionalTestsFor(stage: Stage): List<FunctionalTest>
}

class StatisticBasedGradleBuildBucketProvider(private val model: CIBuildModel, testTimeDataJson: File) : GradleBuildBucketProvider {
    private val buckets: Map<TestCoverage, List<BuildTypeBucket>> = buildBuckets(testTimeDataJson, model)

    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return buckets.getValue(testCoverage).map { it.createFunctionalTestsFor(model, stage, testCoverage) }
    }

    override fun createDeferredFunctionalTestsFor(stage: Stage): List<FunctionalTest> {
        // The first stage which doesn't omit slow projects
        val deferredStage = model.stages.find { !it.omitsSlowProjects }!!
        val deferredStageIndex = model.stages.indexOfFirst { !it.omitsSlowProjects }
        return if (stage.stageName != deferredStage.stageName) {
            emptyList()
        } else {
            val stages = model.stages.subList(0, deferredStageIndex)
            val deferredTests = mutableListOf<FunctionalTest>()
            stages.forEach { eachStage ->
                eachStage.functionalTests.forEach { testConfig ->
                    deferredTests.addAll(model.subprojects.getSlowSubprojects().map { it.createFunctionalTestsFor(model, eachStage, testConfig) })
                }
            }
            deferredTests
        }
    }

    private
    fun buildBuckets(buildClassTimeJson: File, model: CIBuildModel): Map<TestCoverage, List<BuildTypeBucket>> {
        val jsonObj = JSON.parseObject(buildClassTimeJson.readText()) as JSONObject
        val buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes = jsonObj.map { buildProjectToSubprojectTestClassTime ->
            buildProjectToSubprojectTestClassTime.key to (buildProjectToSubprojectTestClassTime.value as JSONObject).map { subProjectToTestClassTime ->
                subProjectToTestClassTime.key to (subProjectToTestClassTime.value as JSONArray).map { TestClassTime(it as JSONObject) }
            }.toMap()
        }.toMap()

        val result = mutableMapOf<TestCoverage, List<BuildTypeBucket>>()
        for (stage in model.stages) {
            for (testCoverage in stage.functionalTests) {
                when (testCoverage.testType) {
                    TestType.allVersionsIntegMultiVersion -> {
                        result[testCoverage] = listOf(AllSubprojectsIntegMultiVersionTest.INSTANCE)
                    }
                    in listOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion) -> {
                        result[testCoverage] = splitBucketsByGradleVersionForBuildProject(6)
                    }
                    else -> {
                        result[testCoverage] = splitBucketsByTestClassesForBuildProject(testCoverage, stage, buildProjectClassTimes)
                    }
                }
            }
        }
        return result
    }

    // For quickFeedbackCrossVersion and allVersionsCrossVersion, the buckets are split by Gradle version
    // By default, split them into [gradle1, gradle2, gradle3, gradle4, gradle5, gradle6]
    private fun splitBucketsByGradleVersionForBuildProject(maxGradleMajorVersion: Int) = (1..maxGradleMajorVersion).map { GradleVersionXCrossVersionTestBucket(it) }

    private
    fun splitBucketsByTestClassesForBuildProject(testCoverage: TestCoverage, stage: Stage, buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes): List<BuildTypeBucket> {
        val validSubprojects = model.subprojects.getSubprojectsFor(testCoverage, stage)

        // Build project not found, don't split into buckets
        val subProjectToClassTimes: Map<String, List<TestClassTime>> = buildProjectClassTimes[testCoverage.asId(model)] ?: return validSubprojects

        val subProjectTestClassTimes: List<SubprojectTestClassTime> = subProjectToClassTimes
            .entries
            .filter { "UNKNOWN" != it.key }
            .filter { model.subprojects.getSubprojectByName(it.key) != null }
            .map { SubprojectTestClassTime(model.subprojects.getSubprojectByName(it.key)!!, it.value.filter { it.sourceSet != "test" }) }
        val expectedBucketSize: Int = subProjectTestClassTimes.sumBy { it.totalTime } / BUCKET_NUMBER

        return split(subProjectTestClassTimes, expectedBucketSize)
    }

    private
    fun split(subprojects: List<SubprojectTestClassTime>, expectedBucketSize: Int): List<BuildTypeBucket> {
        val buckets: List<List<SubprojectTestClassTime>> = split(subprojects, SubprojectTestClassTime::totalTime, expectedBucketSize, 5)
        val ret = mutableListOf<BuildTypeBucket>()
        var bucketNumber = 1
        buckets.forEach { subprojectsInBucket ->
            if (subprojectsInBucket.size == 1) {
                // Split large project to potential multiple buckets
                ret.addAll(subprojectsInBucket[0].split(expectedBucketSize))
            } else {
                ret.add(SmallSubprojectBucket("bucket${bucketNumber++}", subprojectsInBucket.map { it.subProject }))
            }
        }
        return ret
    }
}

/**
 * Split a list of object into buckets with expected size.
 *
 * For example, we have a list of number [9, 1, 2, 10, 4, 5] and the expected size is 5,
 * the result buckets will be [[10], [9], [5], [4, 1], [2]]
 */
fun <T> split(list: List<T>, function: (T) -> Int, expectedBucketSize: Int, maxItemNumberInBucket: Int): List<List<T>> {
    val sortedList = ArrayList(list.sortedBy { -function(it) })
    val ret = mutableListOf<List<T>>()

    while (sortedList.isNotEmpty()) {
        val largest = sortedList.removeAt(0)
        val bucket = mutableListOf<T>()
        var restCapacity = expectedBucketSize - function(largest)

        bucket.add(largest)

        while (true) {
            // Find next largest object which can fit in resetCapacity
            val index = sortedList.indexOfFirst { function(it) < restCapacity }
            if (index == -1 || sortedList.isEmpty() || bucket.size >= maxItemNumberInBucket) {
                // TeamCity has length constraint, don't make a bucket containing too many subprojects.
                break
            }

            val nextElementToAddToBucket = sortedList.removeAt(index)
            restCapacity -= function(nextElementToAddToBucket)
            bucket.add(nextElementToAddToBucket)
        }

        ret.add(bucket)
    }
    return ret
}

enum class AllSubprojectsIntegMultiVersionTest : BuildTypeBucket {
    INSTANCE;

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, "all"),
            testCoverage.asName(),
            "${testCoverage.asName()} for all subprojects",
            testCoverage,
            stage,
            emptyList()
        )
}

class GradleVersionXCrossVersionTestBucket(private val gradleMajorVersion: Int) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, "gradle$gradleMajorVersion"),
            "${testCoverage.asName()} (gradle $gradleMajorVersion)",
            "${testCoverage.asName()} for gradle $gradleMajorVersion",
            testCoverage,
            stage,
            emptyList(),
            "-PonlyTestGradleMajorVersion=$gradleMajorVersion"
        )
}

class LargeSubprojectSplitBucket(private val subProject: GradleSubproject, private val number: Int, private val include: Boolean, private val classes: List<TestClassTime>) : BuildTypeBucket by subProject {
    val name = if (number == 1) subProject.name else "${subProject.name}_$number"

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): FunctionalTest =
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, name),
            "${testCoverage.asName()} ($name)",
            "${testCoverage.asName()} for projects $name",
            testCoverage,
            stage,
            subprojects = listOf(subProject.name),
            extraParameters = if (include) "-PincludeTestClasses=true -x ${subProject.name}:test" else "-PexcludeTestClasses=true", // Only run unit test in last bucket
            preBuildSteps = prepareTestClassesStep(testCoverage.os)
        )

    private fun prepareTestClassesStep(os: Os): BuildSteps.() -> Unit {
        val testClasses = classes.map { it.toPropertiesLine() }
        val action = if (include) "include" else "exclude"
        val unixScript = """
mkdir -p build
rm -rf build/*-test-classes.properties
cat > build/$action-test-classes.properties << EOL
${testClasses.joinToString("\n")}
EOL

echo "Tests to be ${action}d in this build"
cat build/$action-test-classes.properties
"""

        val linesWithEcho = testClasses.joinToString("\n") { "echo $it" }

        val windowsScript = """
mkdir build
del /f /q build\include-test-classes.properties
del /f /q build\exclude-test-classes.properties
(
$linesWithEcho
) > build\$action-test-classes.properties

echo "Tests to be ${action}d in this build"
type build\$action-test-classes.properties
"""

        return {
            script {
                name = "PREPARE_TEST_CLASSES"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                scriptContent = if (os == Os.windows) windowsScript else unixScript
            }
        }
    }
}

class SmallSubprojectBucket(val name: String, private val subprojects: List<GradleSubproject>) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): FunctionalTest =
        FunctionalTest(model, testCoverage.asConfigurationId(model, name),
            "${testCoverage.asName()} (${subprojects.joinToString(", ") { it.name }})",
            "${testCoverage.asName()} for ${subprojects.joinToString(", ") { it.name }}",
            testCoverage,
            stage,
            subprojects.map { it.name }
        )
}

class TestClassTime(var testClass: String, val sourceSet: String, var buildTimeMs: Int) {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("testClass"),
        jsonObject.getString("sourceSet"),
        jsonObject.getIntValue("buildTimeMs")
    )

    fun toPropertiesLine() = "$testClass=$sourceSet"
}

class SubprojectTestClassTime(val subProject: GradleSubproject, private val testClassTimes: List<TestClassTime>) {
    val totalTime: Int = testClassTimes.sumBy { it.buildTimeMs }

    fun split(expectedBuildTimePerBucket: Int): List<BuildTypeBucket> {
        return if (totalTime < 1.1 * expectedBuildTimePerBucket) {
            listOf(subProject)
        } else {
            val buckets: List<List<TestClassTime>> = split(testClassTimes, TestClassTime::buildTimeMs, expectedBuildTimePerBucket, Integer.MAX_VALUE)
            return if (buckets.size == 1) {
                listOf(subProject)
            } else {
                buckets.mapIndexed { index: Int, classesInBucket: List<TestClassTime> ->
                    val include = index != buckets.size - 1
                    val classes = if (include) classesInBucket else buckets.subList(0, buckets.size - 1).flatten()
                    LargeSubprojectSplitBucket(subProject, index + 1, include, classes)
                }
            }
        }
    }
}
