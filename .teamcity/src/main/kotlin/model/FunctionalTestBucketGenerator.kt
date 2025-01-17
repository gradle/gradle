package model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import common.Arch
import common.Os
import common.VersionedSettingsBranch
import configurations.ParallelizationMethod
import java.io.File
import java.util.LinkedList

const val MASTER_CHECK_CONFIGURATION = "Gradle_Master_Check"
const val MAX_PROJECT_NUMBER_IN_BUCKET = 11

/**
 * Process test-class-data.json and generates test-buckets.json
 *
 * Usage: `mvn compile exec:java@update-test-buckets -DinputTestClassDataJson=/path/to/test-class-data.json`.
 * You can get the JSON file as an artifacts of the "autoUpdateTestSplitJsonOnGradleMaster" pipeline in TeamCity.
 */
fun main() {
    val model = CIBuildModel(
        projectId = "Check",
        branch = VersionedSettingsBranch("master"),
        buildScanTags = listOf("Check"),
        subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json"))
    )
    val testClassDataJson = File(System.getProperty("inputTestClassDataJson") ?: throw IllegalArgumentException("Input file not found!"))
    val generatedBucketsJson = File(System.getProperty("outputBucketSplitJson", "./test-buckets.json"))

    FunctionalTestBucketGenerator(model, testClassDataJson).generate(generatedBucketsJson)
}

class TestClassTime(
    val testClassAndSourceSet: TestClassAndSourceSet,
    val buildTimeMs: Int
) {
    constructor(jsonObject: JSONObject) : this(
        TestClassAndSourceSet(
            jsonObject.getString("testClass"),
            jsonObject.getString("sourceSet")
        ),
        jsonObject.getIntValue("buildTimeMs")
    )
}

data class TestCoverageAndBucketSplits(
    val testCoverageUuid: Int,
    val buckets: List<FunctionalTestBucket>
)

data class FunctionalTestBucket(
    val subprojects: List<String>,
    val parallelizationMethod: ParallelizationMethod
) {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getJSONArray("subprojects").map { it.toString() },
        ParallelizationMethod.fromJson(jsonObject)
    )

    fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): SmallSubprojectBucket = SmallSubprojectBucket(
        subprojects.map { gradleSubprojectProvider.getSubprojectByName(it)!! },
        parallelizationMethod
    )
}

class SubprojectTestClassTime(
    val subProject: GradleSubproject,
    testClassTimes: List<TestClassTime> = emptyList()
) {
    val totalTime: Int = testClassTimes.sumOf { it.buildTimeMs }

    override fun toString(): String {
        return "SubprojectTestClassTime(subProject=${subProject.name}, totalTime=$totalTime)"
    }
}

class FunctionalTestBucketGenerator(private val model: CIBuildModel, testTimeDataJson: File) {
    private val buckets: Map<TestCoverage, List<SmallSubprojectBucket>> = buildBuckets(testTimeDataJson, model)

    fun generate(jsonFile: File) {
        jsonFile.writeText(
            JSON.toJSONString(
                buckets.map {
                    TestCoverageAndBucketSplits(it.key.uuid, it.value.map { it.toJsonBucket() })
                },
                SerializerFeature.PrettyFormat,
                SerializerFeature.DisableCircularReferenceDetect
            )
        )
    }

    private
    fun buildBuckets(buildClassTimeJson: File, model: CIBuildModel): Map<TestCoverage, List<SmallSubprojectBucket>> {
        val jsonObj = JSON.parseObject(buildClassTimeJson.readText()) as JSONObject
        val buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes = jsonObj.map { buildProjectToSubprojectTestClassTime ->
            buildProjectToSubprojectTestClassTime.key to (buildProjectToSubprojectTestClassTime.value as JSONObject).map { subProjectToTestClassTime ->
                subProjectToTestClassTime.key to (subProjectToTestClassTime.value as JSONArray).map { TestClassTime(it as JSONObject) }
            }.toMap()
        }.toMap()

        val result = mutableMapOf<TestCoverage, List<SmallSubprojectBucket>>()
        for (stage in model.stages) {
            for (testCoverage in stage.functionalTests) {
                if (testCoverage.testType !in listOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion, TestType.soak)) {
                    result[testCoverage] = splitBucketsByTestClassesForBuildProject(testCoverage, buildProjectClassTimes)
                }
            }
        }
        return result
    }

    private
    fun splitBucketsByTestClassesForBuildProject(testCoverage: TestCoverage, buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes): List<SmallSubprojectBucket> {
        val validSubprojects = model.subprojects.getSubprojectsForFunctionalTest(testCoverage)

        // Build project not found, don't split into buckets
        val subProjectToClassTimes: MutableMap<String, List<TestClassTime>> =
            determineSubProjectClassTimes(testCoverage, buildProjectClassTimes)?.toMutableMap() ?: return validSubprojects.map { SmallSubprojectBucket(it, ParallelizationMethod.None) }

        validSubprojects.forEach {
            if (!subProjectToClassTimes.containsKey(it.name)) {
                subProjectToClassTimes[it.name] = emptyList()
            }
        }

        val subProjectTestClassTimes: List<SubprojectTestClassTime> = subProjectToClassTimes
            .entries
            .filter { "UNKNOWN" != it.key }
            .filter { model.subprojects.getSubprojectByName(it.key) != null }
            .map { SubprojectTestClassTime(model.subprojects.getSubprojectByName(it.key)!!, it.value.filter { it.testClassAndSourceSet.sourceSet != "test" }) }
            .sortedBy { -it.totalTime }
            .filter { onlyNativeSubprojectsForIntelMacs(testCoverage, it.subProject.name) }

        return parallelize(subProjectTestClassTimes, testCoverage) { numberOfBatches ->
            when (testCoverage.os) {
                Os.LINUX -> ParallelizationMethod.TestDistribution
                Os.ALPINE -> ParallelizationMethod.TestDistributionAlpine
                else -> ParallelizationMethod.TeamCityParallelTests(numberOfBatches)
            }
        }
    }

    private fun parallelize(
        subProjectTestClassTimes: List<SubprojectTestClassTime>,
        testCoverage: TestCoverage,
        parallelization: (Int) -> ParallelizationMethod
    ): List<SmallSubprojectBucket> {
        // splitIntoBuckets() method expects us to split large element into N elements,
        // but we want to have a single bucket with N batches.
        // As a workaround, we repeat the bucket N times, and deduplicate the result at the end
        val resultIncludingDuplicates = splitIntoBuckets(
            LinkedList(subProjectTestClassTimes),
            SubprojectTestClassTime::totalTime,
            { largeElement, factor ->
                List(factor) { SmallSubprojectBucket(largeElement.subProject, parallelization(factor)) }
            },
            { list ->
                SmallSubprojectBucket(list.map { it.subProject }, parallelization(1))
            },
            testCoverage.expectedBucketNumber,
            MAX_PROJECT_NUMBER_IN_BUCKET
        )
        return resultIncludingDuplicates.distinctBy { it.name }
    }

    private fun determineSubProjectClassTimes(testCoverage: TestCoverage, buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes): Map<String, List<TestClassTime>>? {
        val testCoverageId = testCoverage.asId(MASTER_CHECK_CONFIGURATION)
        return buildProjectClassTimes[testCoverageId] ?: if (testCoverage.testType == TestType.soak) {
            null
        } else {
            val testCoverages = model.stages.flatMap { it.functionalTests }
            val foundTestCoverage = testCoverages.firstOrNull {
                it.testType == TestType.platform &&
                    it.os == testCoverage.os &&
                    it.arch == testCoverage.arch &&
                    it.buildJvm == testCoverage.buildJvm
            }
            foundTestCoverage?.let {
                buildProjectClassTimes[it.asId(MASTER_CHECK_CONFIGURATION)]
            }?.also {
                println("No test statistics found for ${testCoverage.asName()} (${testCoverage.uuid}), re-using the data from ${foundTestCoverage.asName()} (${foundTestCoverage.uuid})")
            }
        }
    }
}

fun onlyNativeSubprojectsForIntelMacs(
    testCoverage: TestCoverage,
    subprojectName: String
): Boolean {
    return if (testCoverage.os == Os.MACOS && testCoverage.arch == Arch.AMD64) {
        subprojectName.contains("native") ||
            // Include precondition-tester here so we understand that tests do run on macOS intel as well
            subprojectName in listOf("file-watching", "snapshots", "workers", "logging", "precondition-tester")
    } else {
        true
    }
}
