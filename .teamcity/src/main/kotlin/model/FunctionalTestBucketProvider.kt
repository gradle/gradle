package model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import common.Os
import configurations.FunctionalTest
import configurations.ParallelizationMethod
import java.io.File

/**
 * QuickCrossVersionTest only tests the last minor for each major version in the range.
 */
val QUICK_CROSS_VERSION_BUCKETS =
    listOf(
        listOf("0.0", "5.0"), // 0.0 <= version < 5.0
        listOf("5.0", "6.0"), // 5.0 <=version < 6.0
        listOf("6.0", "7.0"), // 6.0 <=version < 7.0
        listOf("7.0", "8.0"), // 7.0 <=version < 8.0
        listOf("8.0", "9.0"), // 8.0 <=version < 9.0
        listOf("9.0", "99.0"), // 9.0 <=version < 99.0
    )

/**
 * Buckets for AllVersionsCrossVersionTest on operating systems that use TestDistribution
 * (see [GradleVersionRangeCrossVersionTestBucket]). TestDistribution spreads the test classes of a
 * bucket over many executors, so a build's duration does not follow the amount of work in its range
 * and coarse buckets are good enough.
 */
val ALL_CROSS_VERSION_BUCKETS =
    listOf(
        listOf("0.0", "5.0"), // 0.0 <= version < 5.0
        listOf("5.0", "6.0"), // 5.0 <=version < 6.0
        listOf("6.0", "7.3"), // 6.0 <=version < 7.3
        listOf("7.3", "7.6"), // 7.3 <=version < 7.6
        listOf("7.6", "8.2"), // 7.6 <=version < 8.2
        listOf("8.2", "8.4"), // 8.2 <=version < 8.4
        listOf("8.4", "8.6"), // 8.4 <=version < 8.6
        listOf("8.6", "8.8"), // 8.6 <=version < 8.8
        listOf("8.8", "8.10"), // 8.8 <=version < 8.10
        listOf("8.10", "8.12"), // 8.10 <=version < 8.12
        listOf("8.12", "8.13"), // 8.12 <=version < 8.13
        listOf("8.13", "9.0"), // 8.13 <=version < 9.0
        listOf("9.0", "9.3"), // 9.0 <=version < 9.3
        listOf("9.3", "99.0"), // 9.3 <=version < 99.0
    )

/**
 * Buckets for AllVersionsCrossVersionTest on operating systems without TestDistribution
 * (see [GradleVersionRangeCrossVersionTestBucket]). Such a bucket runs on a single agent, so its
 * duration is proportional to the test time of the versions it covers and the ranges have to be
 * balanced by that test time to keep every build under an hour.
 *
 * Test time per version grows steeply with the version, which is why the ranges get narrower
 * towards the top and cover a single version from 8.1 onwards. The open-ended last bucket grows
 * with every new minor version, so a new bucket has to be split off it regularly.
 */
val ALL_CROSS_VERSION_BUCKETS_WITHOUT_TEST_DISTRIBUTION =
    listOf(
        listOf("0.0", "5.2"), // 0.0 <= version < 5.2
        listOf("5.2", "6.0"), // 5.2 <=version < 6.0
        listOf("6.0", "6.4"), // 6.0 <=version < 6.4
        listOf("6.4", "6.7"), // 6.4 <=version < 6.7
        listOf("6.7", "7.0"), // 6.7 <=version < 7.0
        listOf("7.0", "7.2"), // 7.0 <=version < 7.2
        listOf("7.2", "7.4"), // 7.2 <=version < 7.4
        listOf("7.4", "7.6"), // 7.4 <=version < 7.6
        listOf("7.6", "8.1"), // 7.6 <=version < 8.1
        listOf("8.1", "8.2"), // 8.1 <=version < 8.2
        listOf("8.2", "8.3"), // 8.2 <=version < 8.3
        listOf("8.3", "8.4"), // 8.3 <=version < 8.4
        listOf("8.4", "8.5"), // 8.4 <=version < 8.5
        listOf("8.5", "8.6"), // 8.5 <=version < 8.6
        listOf("8.6", "8.7"), // 8.6 <=version < 8.7
        listOf("8.7", "8.8"), // 8.7 <=version < 8.8
        listOf("8.8", "8.9"), // 8.8 <=version < 8.9
        listOf("8.9", "8.10"), // 8.9 <=version < 8.10
        listOf("8.10", "8.11"), // 8.10 <=version < 8.11
        listOf("8.11", "8.12"), // 8.11 <=version < 8.12
        listOf("8.12", "8.13"), // 8.12 <=version < 8.13
        listOf("8.13", "8.14"), // 8.13 <=version < 8.14
        listOf("8.14", "9.0"), // 8.14 <=version < 9.0
        listOf("9.0", "9.1"), // 9.0 <=version < 9.1
        listOf("9.1", "9.2"), // 9.1 <=version < 9.2
        listOf("9.2", "9.3"), // 9.2 <=version < 9.3
        listOf("9.3", "9.4"), // 9.3 <=version < 9.4
        listOf("9.4", "9.5"), // 9.4 <=version < 9.5
        listOf("9.5", "9.6"), // 9.5 <=version < 9.6
        listOf("9.6", "99.0"), // 9.6 <=version < 99.0
    )

fun crossVersionTestParallelizationMethod(os: Os): ParallelizationMethod =
    when (os) {
        Os.LINUX -> ParallelizationMethod.TestDistribution
        else -> ParallelizationMethod.None
    }

/**
 * Derived from [crossVersionTestParallelizationMethod] so that the bucket ranges and the way a
 * bucket is parallelized can never get out of sync.
 */
fun allCrossVersionBucketsFor(os: Os): List<List<String>> =
    if (crossVersionTestParallelizationMethod(os) == ParallelizationMethod.TestDistribution) {
        ALL_CROSS_VERSION_BUCKETS
    } else {
        ALL_CROSS_VERSION_BUCKETS_WITHOUT_TEST_DISTRIBUTION
    }

typealias BuildProjectToSubprojectTestClassTimes = Map<String, Map<String, List<TestClassTime>>>

interface FunctionalTestBucketProvider {
    fun createFunctionalTestsFor(
        stage: Stage,
        testCoverage: TestCoverage,
    ): List<FunctionalTest>
}

class DefaultFunctionalTestBucketProvider(
    val model: CIBuildModel,
    testBucketsJson: File,
) : FunctionalTestBucketProvider {
    private val allCrossVersionTestBucketProviders =
        Os.entries.associateWith { CrossVersionTestBucketProvider(allCrossVersionBucketsFor(it), model) }
    private val quickCrossVersionTestBucketProvider = CrossVersionTestBucketProvider(QUICK_CROSS_VERSION_BUCKETS, model)
    private val functionalTestBucketProvider = StatisticBasedFunctionalTestBucketProvider(model, testBucketsJson)

    override fun createFunctionalTestsFor(
        stage: Stage,
        testCoverage: TestCoverage,
    ): List<FunctionalTest> =
        when {
            testCoverage.testType == TestType.QUICK_FEEDBACK_CROSS_VERSION -> {
                quickCrossVersionTestBucketProvider.createFunctionalTestsFor(
                    stage,
                    testCoverage,
                )
            }

            testCoverage.testType == TestType.ALL_VERSIONS_CROSS_VERSION -> {
                allCrossVersionTestBucketProviders.getValue(testCoverage.os).createFunctionalTestsFor(
                    stage,
                    testCoverage,
                )
            }

            else -> {
                functionalTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
            }
        }
}

class CrossVersionTestBucketProvider(
    crossVersionBuckets: List<List<String>>,
    private val model: CIBuildModel,
) : FunctionalTestBucketProvider {
    private val buckets: List<BuildTypeBucket> = crossVersionBuckets.map { GradleVersionRangeCrossVersionTestBucket(it[0], it[1]) }

    // For quickFeedbackCrossVersion and allVersionsCrossVersion, the buckets are split by Gradle version
    // By default, split them by CROSS_VERSION_BUCKETS
    override fun createFunctionalTestsFor(
        stage: Stage,
        testCoverage: TestCoverage,
    ): List<FunctionalTest> = buckets.mapIndexed { index, bucket -> bucket.createFunctionalTestsFor(model, stage, testCoverage, index + 1) }
}

class StatisticBasedFunctionalTestBucketProvider(
    val model: CIBuildModel,
    testBucketsJson: File,
) : FunctionalTestBucketProvider {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val buckets: Map<TestCoverage, List<BuildTypeBucket>> by lazy {
        val uuidToTestCoverage = model.stages.flatMap { it.functionalTests }.associateBy { it.uuid }
        val testCoverageAndBuckets: List<Map<String, Any>> = objectMapper.readValue(testBucketsJson.readText())
        testCoverageAndBuckets.associate { testCoverageAndBucket ->
            val testCoverage: TestCoverage = uuidToTestCoverage.getValue(testCoverageAndBucket["testCoverageUuid"].toString().toInt())
            val buckets: List<SmallSubprojectBucket> =
                (testCoverageAndBucket["buckets"] as List<Map<String, Any>>).map {
                    FunctionalTestBucket(it).toBuildTypeBucket(model.subprojects)
                }

            // Sometimes people may add new subproject into `subprojects.json`
            // in this case we have no historical test running time, so we simply add these subprojects into first available bucket
            val allSubprojectsInBucketJson = buckets.flatMap { it.subprojects.map { it.name } }.toSet()

            val allSubprojectsInModel =
                model.subprojects
                    .getSubprojectsForFunctionalTest(testCoverage)
                    .filter { onlyNativeSubprojectsForIntelMacs(testCoverage, it.name) }
                    .map { it.name }
            val subprojectsInModelButNotInBucketJson = allSubprojectsInModel.toMutableList().apply { removeAll(allSubprojectsInBucketJson) }

            if (subprojectsInModelButNotInBucketJson.isEmpty()) {
                testCoverage to buckets
            } else {
                testCoverage to
                    mergeUnknownSubprojectsIntoFirstAvailableBucket(
                        buckets,
                        model.subprojects.subprojects.filter {
                            subprojectsInModelButNotInBucketJson.contains(it.name)
                        },
                    )
            }
        }
    }

    private fun mergeUnknownSubprojectsIntoFirstAvailableBucket(
        buckets: List<BuildTypeBucket>,
        unknownSubprojects: List<GradleSubproject>,
    ): MutableList<BuildTypeBucket> =
        buckets.toMutableList().apply {
            val firstAvailableBucketIndex =
                indexOfFirst {
                    it is SmallSubprojectBucket &&
                        (
                            it.parallelizationMethod !is ParallelizationMethod.TeamCityParallelTests ||
                                it.parallelizationMethod.numberOfBatches == 1
                        )
                }
            val firstSmallSubprojectsBucket = get(firstAvailableBucketIndex) as SmallSubprojectBucket

            set(
                firstAvailableBucketIndex,
                SmallSubprojectBucket(
                    firstSmallSubprojectsBucket.subprojects + unknownSubprojects,
                    firstSmallSubprojectsBucket.parallelizationMethod,
                ),
            )
        }

    override fun createFunctionalTestsFor(
        stage: Stage,
        testCoverage: TestCoverage,
    ): List<FunctionalTest> =
        buckets.getValue(testCoverage).mapIndexed { bucketIndex: Int, bucket: BuildTypeBucket ->
            bucket.createFunctionalTestsFor(model, stage, testCoverage, bucketIndex)
        }
}

class GradleVersionRangeCrossVersionTestBucket(
    private val startInclusive: String,
    private val endExclusive: String,
) : BuildTypeBucket {
    override fun createFunctionalTestsFor(
        model: CIBuildModel,
        stage: Stage,
        testCoverage: TestCoverage,
        bucketIndex: Int,
    ): FunctionalTest =
        FunctionalTest(
            model,
            testCoverage.getBucketUuid(model, bucketIndex),
            "${testCoverage.asName()} ($startInclusive <= gradle <$endExclusive)",
            "${testCoverage.asName()} for gradle ($startInclusive <= gradle <$endExclusive)",
            testCoverage,
            stage,
            crossVersionTestParallelizationMethod(testCoverage.os),
            emptyList(),
            extraParameters = "-PonlyTestGradleVersion=$startInclusive-$endExclusive",
        )
}
