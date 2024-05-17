package model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import common.Os
import configurations.FunctionalTest
import configurations.ParallelizationMethod
import java.io.File

/**
 * QuickCrossVersionTest only tests the last minor for each major version in the range.
 */
val QUICK_CROSS_VERSION_BUCKETS = listOf(
    listOf("0.0", "3.0"), // 0.0 <= version < 3.0
    listOf("3.0", "4.0"), // 3.0 <= version < 4.0
    listOf("4.0", "5.0"), // 4.0 <= version < 5.0
    listOf("5.0", "6.0"), // 5.0 <=version < 6.0
    listOf("6.0", "7.0"), // 6.0 <=version < 7.0
    listOf("7.0", "8.0"), // 7.0 <=version < 8.0
    listOf("8.0", "9.0"), // 8.0 <=version < 9.0
    listOf("9.0", "10.0"), // 9.0 <=version < 10.0
    listOf("10.0", "11.0"), // 10.0 <=version < 11.0
    listOf("11.0", "12.0"), // 11.0 <=version < 12.0
    listOf("12.0", "13.0"), // 12.0 <=version < 13.0
    listOf("13.0", "99.0") // 13.0 <=version < 99.0
)

val ALL_CROSS_VERSION_BUCKETS = listOf(
    listOf("0.0", "2.8"), // 0.0 <= version < 2.8
    listOf("2.8", "3.3"), // 2.8 <= version < 3.3
    listOf("3.3", "4.1"), // 3.3 <= version < 4.1
    listOf("4.1", "4.5"), // 4.1 <=version < 4.5
    listOf("4.5", "4.8"), // 4.5 <=version < 4.8
    listOf("4.8", "5.0"), // 4.8 <=version < 5.0
    listOf("5.0", "5.4"), // 5.0 <=version < 5.4
    listOf("5.4", "5.5"), // 5.4 <=version < 5.5
    listOf("5.5", "6.1"), // 5.5 <=version < 6.1
    listOf("6.1", "6.4"), // 6.1 <=version < 6.4
    listOf("6.4", "6.7"), // 6.4 <=version < 6.7
    listOf("6.7", "7.0"), // 6.7 <=version < 7.0
    listOf("7.0", "7.3"), // 7.0 <=version < 7.3
    listOf("7.3", "7.6"), // 7.3 <=version < 7.6
    listOf("7.6", "8.3"), // 7.6 <=version < 8.3
    listOf("8.3", "99.0") // 8.3 <=version < 99.0
)

typealias BuildProjectToSubprojectTestClassTimes = Map<String, Map<String, List<TestClassTime>>>

interface FunctionalTestBucketProvider {
    fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest>
}

class DefaultFunctionalTestBucketProvider(val model: CIBuildModel, testBucketsJson: File) : FunctionalTestBucketProvider {
    private val allCrossVersionTestBucketProvider = CrossVersionTestBucketProvider(ALL_CROSS_VERSION_BUCKETS, model)
    private val quickCrossVersionTestBucketProvider = CrossVersionTestBucketProvider(QUICK_CROSS_VERSION_BUCKETS, model)
    private val functionalTestBucketProvider = StatisticBasedFunctionalTestBucketProvider(model, testBucketsJson)
    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return when {
            testCoverage.testType == TestType.quickFeedbackCrossVersion -> quickCrossVersionTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
            testCoverage.testType == TestType.allVersionsCrossVersion -> allCrossVersionTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
            else -> functionalTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
        }
    }
}

class CrossVersionTestBucketProvider(
    crossVersionBuckets: List<List<String>>,
    private val model: CIBuildModel
) : FunctionalTestBucketProvider {
    private val buckets: List<BuildTypeBucket> = crossVersionBuckets.map { GradleVersionRangeCrossVersionTestBucket(it[0], it[1]) }

    // For quickFeedbackCrossVersion and allVersionsCrossVersion, the buckets are split by Gradle version
    // By default, split them by CROSS_VERSION_BUCKETS
    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return buckets.mapIndexed { index, bucket -> bucket.createFunctionalTestsFor(model, stage, testCoverage, index + 1) }
    }
}

class StatisticBasedFunctionalTestBucketProvider(val model: CIBuildModel, testBucketsJson: File) : FunctionalTestBucketProvider {
    private val buckets: Map<TestCoverage, List<BuildTypeBucket>> by lazy {
        val uuidToTestCoverage = model.stages.flatMap { it.functionalTests }.associateBy { it.uuid }
        val testCoverageAndBuckets = JSON.parseArray(testBucketsJson.readText()) as JSONArray
        testCoverageAndBuckets.associate { testCoverageAndBucket ->
            testCoverageAndBucket as JSONObject
            val testCoverage: TestCoverage = uuidToTestCoverage.getValue(testCoverageAndBucket.getIntValue("testCoverageUuid"))
            val buckets: List<SmallSubprojectBucket> = testCoverageAndBucket.getJSONArray("buckets").map {
                FunctionalTestBucket(it as JSONObject).toBuildTypeBucket(model.subprojects)
            }

            // Sometimes people may add new subproject into `subprojects.json`
            // in this case we have no historical test running time, so we simply add these subprojects into first available bucket
            val allSubprojectsInBucketJson = buckets.flatMap { it.subprojects.map { it.name } }.toSet()

            val allSubprojectsInModel = model.subprojects.getSubprojectsForFunctionalTest(testCoverage).map { it.name }
            val subprojectsInModelButNotInBucketJson = allSubprojectsInModel.toMutableList().apply { removeAll(allSubprojectsInBucketJson) }

            if (subprojectsInModelButNotInBucketJson.isEmpty()) {
                testCoverage to buckets
            } else {
                testCoverage to mergeUnknownSubprojectsIntoFirstAvailableBucket(buckets, model.subprojects.subprojects.filter { subprojectsInModelButNotInBucketJson.contains(it.name) })
            }
        }
    }

    private fun mergeUnknownSubprojectsIntoFirstAvailableBucket(buckets: List<BuildTypeBucket>, unknownSubprojects: List<GradleSubproject>): MutableList<BuildTypeBucket> =
        buckets.toMutableList().apply {
            val firstAvailableBucketIndex = indexOfFirst {
                it is SmallSubprojectBucket &&
                    (it.parallelizationMethod !is ParallelizationMethod.TeamCityParallelTests || it.parallelizationMethod.numberOfBatches == 1)
            }
            val firstSmallSubprojectsBucket = get(firstAvailableBucketIndex) as SmallSubprojectBucket

            set(firstAvailableBucketIndex, SmallSubprojectBucket(firstSmallSubprojectsBucket.subprojects + unknownSubprojects, firstSmallSubprojectsBucket.parallelizationMethod))
        }

    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return buckets.getValue(testCoverage).mapIndexed { bucketIndex: Int, bucket: BuildTypeBucket ->
            bucket.createFunctionalTestsFor(model, stage, testCoverage, bucketIndex)
        }
    }
}

class GradleVersionRangeCrossVersionTestBucket(private val startInclusive: String, private val endExclusive: String) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, bucketIndex: Int): FunctionalTest {
        val parallelizationMethod = when (testCoverage.os) {
            Os.LINUX -> ParallelizationMethod.TestDistribution
            else -> ParallelizationMethod.None
        }

        return FunctionalTest(
            model,
            testCoverage.getBucketUuid(model, bucketIndex),
            "${testCoverage.asName()} ($startInclusive <= gradle <$endExclusive)",
            "${testCoverage.asName()} for gradle ($startInclusive <= gradle <$endExclusive)",
            testCoverage,
            stage,
            parallelizationMethod,
            emptyList(),
            extraParameters = "-PonlyTestGradleVersion=$startInclusive-$endExclusive"
        )
    }
}

data class TestClassAndSourceSet(
    val testClass: String,
    val sourceSet: String
)

data class SmallSubprojectBucket(
    val subprojects: List<GradleSubproject>,
    val parallelizationMethod: ParallelizationMethod
) : BuildTypeBucket {
    constructor(subproject: GradleSubproject, parallelizationMethod: ParallelizationMethod) : this(listOf(subproject), parallelizationMethod)

    val name = truncateName(subprojects.joinToString(","))

    private fun truncateName(str: String) =
        // Can't exceed Linux file name limit 255 char on TeamCity
        if (str.length > 200) {
            str.substring(0, 200) + "..."
        } else {
            str
        }

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, bucketIndex: Int): FunctionalTest =
        FunctionalTest(
            model,
            testCoverage.getBucketUuid(model, bucketIndex),
            getName(testCoverage),
            getDescription(testCoverage),
            testCoverage,
            stage,
            parallelizationMethod,
            subprojects.map { it.name }
        )

    override fun getName(testCoverage: TestCoverage) = truncateName("${testCoverage.asName()} (${subprojects.joinToString(",") { it.name }})")

    override fun getDescription(testCoverage: TestCoverage) = "${testCoverage.asName()} for ${subprojects.joinToString(", ") { it.name }}"

    fun toJsonBucket(): FunctionalTestBucket {
        return FunctionalTestBucket(subprojects.map { it.name }, parallelizationMethod)
    }
}
