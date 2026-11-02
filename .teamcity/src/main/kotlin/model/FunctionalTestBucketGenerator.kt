package model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
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
    val model =
        CIBuildModel(
            projectId = "Check",
            branch = VersionedSettingsBranch("master"),
            buildScanTags = listOf("Check"),
            subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json")),
        )
    val testClassDataJson = File(System.getProperty("inputTestClassDataJson") ?: throw IllegalArgumentException("Input file not found!"))
    val generatedBucketsJson = File(System.getProperty("outputBucketSplitJson", "./test-buckets.json"))

    // The layout to preserve always comes from the checked-in file, never from the output path: writing
    // the split elsewhere (a dry run, say) must still produce the order the checked-in file has, otherwise
    // that output cannot be compared against it.
    val fullRebalance = System.getProperty("fullRebalance")?.toBoolean() ?: false
    FunctionalTestBucketGenerator(model, testClassDataJson, fullRebalance).generate(generatedBucketsJson, File("./test-buckets.json"))
}

class FunctionalTestBucketGenerator(
    private val model: CIBuildModel,
    private val testTimeDataJson: File,
    private val fullRebalance: Boolean = false,
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Writes the new split to [jsonFile], keeping every bucket at the position its predecessor in
     * [previousBucketsJson] occupied. The bucket position determines the id of the TeamCity build
     * configuration generated from it, so preserving it keeps that configuration's caches valid.
     *
     * [previousBucketsJson] defaults to [jsonFile], which is the file being regenerated in place.
     */
    @JvmOverloads
    fun generate(
        jsonFile: File,
        previousBucketsJson: File = jsonFile,
    ) {
        val previousLayout = PreviousBucketLayout.readFrom(previousBucketsJson)
        val output =
            buildBuckets(previousLayout).map { (testCoverage, testCoverageBuckets) ->
                TestCoverageAndBucketSplits(testCoverage.uuid, testCoverageBuckets)
            }
        jsonFile.writeText(gson.toJson(output))
    }

    private fun buildBuckets(previousLayout: PreviousBucketLayout): Map<TestCoverage, List<FunctionalTestBucket>> {
        val sType = object : TypeToken<BuildProjectToSubprojectTestClassTimes>() {}.type
        val buildProjectClassTimes = gson.fromJson<BuildProjectToSubprojectTestClassTimes>(testTimeDataJson.readText(), sType)

        val result = mutableMapOf<TestCoverage, List<FunctionalTestBucket>>()
        for (stage in model.stages) {
            for (testCoverage in stage.functionalTests) {
                if (testCoverage.testType !in
                    listOf(TestType.ALL_VERSIONS_CROSS_VERSION, TestType.QUICK_FEEDBACK_CROSS_VERSION, TestType.SOAK)
                ) {
                    result[testCoverage] =
                        splitBucketsByTestClassesForBuildProject(
                            testCoverage,
                            buildProjectClassTimes,
                            previousLayout.bucketsFor(testCoverage.uuid),
                        )
                }
            }
        }
        return result
    }

    private fun splitBucketsByTestClassesForBuildProject(
        testCoverage: TestCoverage,
        buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes,
        previousBuckets: List<FunctionalTestBucket>,
    ): List<FunctionalTestBucket> {
        val validSubprojects = model.subprojects.getSubprojectsForFunctionalTest(testCoverage)

        // Build project not found, don't split into buckets
        val subProjectToClassTimes: MutableMap<String, List<TestClassTime>> =
            determineSubProjectClassTimes(testCoverage, buildProjectClassTimes)?.toMutableMap()
                ?: return validSubprojects.map { FunctionalTestBucket(listOf(it.name), ParallelizationMethod.None) }

        validSubprojects.forEach {
            if (!subProjectToClassTimes.containsKey(it.name)) {
                subProjectToClassTimes[it.name] = emptyList()
            }
        }

        val subProjectTestClassTimes: List<SubprojectTestClassTime> =
            subProjectToClassTimes
                .entries
                .filter { "UNKNOWN" != it.key }
                .filter { model.subprojects.getSubprojectByName(it.key) != null }
                .map {
                    SubprojectTestClassTime(
                        model.subprojects.getSubprojectByName(it.key)!!,
                        it.value.filter { tct ->
                            tct.sourceSet != "test"
                        },
                    )
                }.sortedBy { -it.totalTime }
                .filter { onlyNativeSubprojectsForIntelMacs(testCoverage, it.subProject.name) }

        val parallelization = { numberOfBatches: Int ->
            when (testCoverage.os) {
                Os.LINUX -> ParallelizationMethod.TestDistribution
                Os.ALPINE -> ParallelizationMethod.TestDistributionAlpine
                else -> ParallelizationMethod.TeamCityParallelTests(numberOfBatches)
            }
        }

        if (!fullRebalance && previousBuckets.isNotEmpty()) {
            return rebalanceIncrementally(
                previousBuckets,
                subProjectTestClassTimes.associate { it.subProject.name to it.totalTime },
                parallelization(1),
            )
        }

        return assignToPreviousSlots(
            parallelize(subProjectTestClassTimes, testCoverage, parallelization),
            previousBuckets.map { it.subprojects },
        ) { bucket -> bucket.subprojects.map { it.name }.toSet() }.map { it.toJsonBucket() }
    }

    private fun parallelize(
        subProjectTestClassTimes: List<SubprojectTestClassTime>,
        testCoverage: TestCoverage,
        parallelization: (Int) -> ParallelizationMethod,
    ): List<SmallSubprojectBucket> {
        // splitIntoBuckets() method expects us to split large element into N elements,
        // but we want to have a single bucket with N batches.
        // As a workaround, we repeat the bucket N times, and deduplicate the result at the end
        val resultIncludingDuplicates =
            splitIntoBuckets(
                LinkedList(subProjectTestClassTimes),
                SubprojectTestClassTime::totalTime,
                { largeElement, factor ->
                    List(factor) { SmallSubprojectBucket(largeElement.subProject, parallelization(factor)) }
                },
                { list ->
                    SmallSubprojectBucket(list.map { it.subProject }, parallelization(1))
                },
                testCoverage.expectedBucketNumber,
                MAX_PROJECT_NUMBER_IN_BUCKET,
            )
        return resultIncludingDuplicates.distinctBy { it.name }
    }

    private fun determineSubProjectClassTimes(
        testCoverage: TestCoverage,
        buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes,
    ): Map<String, List<TestClassTime>>? {
        val testCoverageId = testCoverage.asId(MASTER_CHECK_CONFIGURATION)
        return buildProjectClassTimes[testCoverageId] ?: if (testCoverage.testType == TestType.SOAK) {
            null
        } else {
            val testCoverages = model.stages.flatMap { it.functionalTests }
            val foundTestCoverage =
                testCoverages.firstOrNull {
                    it.testType == TestType.PLATFORM &&
                        it.os == testCoverage.os &&
                        it.arch == testCoverage.arch &&
                        it.buildJvm == testCoverage.buildJvm
                }
            foundTestCoverage
                ?.let {
                    buildProjectClassTimes[it.asId(MASTER_CHECK_CONFIGURATION)]
                }?.also {
                    println(
                        "No test statistics found for ${testCoverage.asName()} (${testCoverage.uuid}), re-using the data from ${foundTestCoverage.asName()} (${foundTestCoverage.uuid})",
                    )
                }
        }
    }
}

fun onlyNativeSubprojectsForIntelMacs(
    testCoverage: TestCoverage,
    subprojectName: String,
): Boolean =
    if (testCoverage.os == Os.MACOS && testCoverage.arch == Arch.AMD64) {
        subprojectName.contains("native") ||
            // Include precondition-tester here so we understand that tests do run on macOS intel as well
            subprojectName in listOf("file-watching", "snapshots", "workers", "logging", "precondition-tester")
    } else {
        true
    }
