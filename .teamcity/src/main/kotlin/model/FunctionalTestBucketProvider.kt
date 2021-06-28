package model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import common.Os
import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import java.io.File

const val MASTER_CHECK_CONFIGURATION = "Gradle_Master_Check"
const val MAX_PROJECT_NUMBER_IN_BUCKET = 11

val CROSS_VERSION_BUCKETS = listOf(
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
    listOf("6.7", "99.0") // 6.7 <=version < 99.0
)

typealias BuildProjectToSubprojectTestClassTimes = Map<String, Map<String, List<TestClassTime>>>

interface FunctionalTestBucketProvider {
    fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest>
}

class DefaultFunctionalTestBucketProvider(val model: CIBuildModel, testBucketsJson: File) : FunctionalTestBucketProvider {
    private val crossVersionTestBucketProvider = CrossVersionTestBucketProvider(model)
    private val functionalTestBucketProvider = StatisticBasedFunctionalTestBucketProvider(model, testBucketsJson)
    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return if (testCoverage.testType in listOf(TestType.quickFeedbackCrossVersion, TestType.allVersionsCrossVersion)) {
            crossVersionTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
        } else {
            functionalTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
        }
    }
}

class CrossVersionTestBucketProvider(private val model: CIBuildModel) : FunctionalTestBucketProvider {
    private val buckets: List<BuildTypeBucket> = CROSS_VERSION_BUCKETS.map { GradleVersionRangeCrossVersionTestBucket(it[0], it[1]) }

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
            val buckets: List<BuildTypeBucket> = testCoverageAndBucket.getJSONArray("buckets").map {
                fromJsonObject(it as JSONObject).toBuildTypeBucket(model.subprojects)
            }
            testCoverage to buckets
        }
    }

    override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        return buckets.getValue(testCoverage).mapIndexed { bucketIndex: Int, bucket: BuildTypeBucket ->
            bucket.createFunctionalTestsFor(model, stage, testCoverage, bucketIndex)
        }
    }
}

class GradleVersionRangeCrossVersionTestBucket(private val startInclusive: String, private val endExclusive: String) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, bucketIndex: Int) =
        FunctionalTest(
            model,
            getUuid(model, testCoverage, bucketIndex),
            "${testCoverage.asName()} ($startInclusive <= gradle <$endExclusive)",
            "${testCoverage.asName()} for gradle ($startInclusive <= gradle <$endExclusive)",
            testCoverage,
            stage,
            emptyList(),
            extraParameters = "-PonlyTestGradleVersion=$startInclusive-$endExclusive"
        )
}

class TestClassAndSourceSet(
    val testClass: String,
    val sourceSet: String
) {
    constructor(classAndSourceSet: String) : this(
        classAndSourceSet.substringBefore("="),
        classAndSourceSet.substringAfter("=")
    )

    fun toPropertiesLine() = "$testClass=$sourceSet"
}

class LargeSubprojectSplitBucket(
    val subproject: GradleSubproject,
    val number: Int,
    val include: Boolean,
    val classes: List<TestClassAndSourceSet>
) : BuildTypeBucket by subproject {
    val name = "${subproject.name}_$number"

    override fun getName(testCoverage: TestCoverage) = "${testCoverage.asName()} ($name)"

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, bucketIndex: Int): FunctionalTest =
        FunctionalTest(
            model,
            getUuid(model, testCoverage, bucketIndex),
            getName(testCoverage),
            getDescription(testCoverage),
            testCoverage,
            stage,
            subprojects = listOf(subproject.name),
            extraParameters = if (include) "-PincludeTestClasses=true -x ${subproject.name}:test" else "-PexcludeTestClasses=true", // Only run unit test in last bucket
            preBuildSteps = prepareTestClassesStep(testCoverage.os)
        )

    private fun prepareTestClassesStep(os: Os): BuildSteps.() -> Unit {
        val testClasses = classes.map { it.toPropertiesLine() }
        val action = if (include) "include" else "exclude"
        val unixScript = """
mkdir -p test-splits
rm -rf test-splits/*-test-classes.properties
cat > test-splits/$action-test-classes.properties << EOL
${testClasses.joinToString("\n")}
EOL

echo "Tests to be ${action}d in this build"
cat test-splits/$action-test-classes.properties
"""

        val linesWithEcho = testClasses.joinToString("\n") { "echo $it" }

        val windowsScript = """
mkdir test-splits
del /f /q test-splits\include-test-classes.properties
del /f /q test-splits\exclude-test-classes.properties
(
$linesWithEcho
) > test-splits\$action-test-classes.properties

echo "Tests to be ${action}d in this build"
type test-splits\$action-test-classes.properties
"""

        return {
            script {
                name = "PREPARE_TEST_CLASSES"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                scriptContent = if (os == Os.WINDOWS) windowsScript else unixScript
            }
        }
    }
}

class SmallSubprojectBucket(
    val subprojects: List<GradleSubproject>,
    val enableTestDistribution: Boolean = false
) : BuildTypeBucket {
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
            getUuid(model, testCoverage, bucketIndex),
            getName(testCoverage),
            getDescription(testCoverage),
            testCoverage,
            stage,
            subprojects.map { it.name },
            enableTestDistribution
        )

    override fun getName(testCoverage: TestCoverage) = truncateName("${testCoverage.asName()} (${subprojects.joinToString(",") { it.name }})")

    override fun getDescription(testCoverage: TestCoverage) = "${testCoverage.asName()} for ${subprojects.joinToString(", ") { it.name }}"
}
