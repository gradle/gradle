package model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import common.Os
import common.VersionedSettingsBranch
import java.io.File
import java.util.LinkedList

const val MASTER_CHECK_CONFIGURATION = "Gradle_Master_Check"
const val MAX_PROJECT_NUMBER_IN_BUCKET = 11

/**
 * Process test-class-data.json and generates test-buckets.json
 *
 * Usage: `mvn compile exec:java@update-test-buckets -DinputTestClassDataJson=/path/to/test-class-data.json`.
 */
fun main() {
    val model = CIBuildModel(
        projectId = "Check",
        branch = VersionedSettingsBranch("master", true),
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

interface FunctionalTestBucket {
    fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): BuildTypeBucket
}

fun fromJsonObject(jsonObject: JSONObject): FunctionalTestBucket = if (jsonObject.containsKey("classes")) {
    FunctionalTestBucketWithSplitClasses(jsonObject)
} else {
    MultipleSubprojectsFunctionalTestBucket(jsonObject)
}

data class FunctionalTestBucketWithSplitClasses(
    val subproject: String,
    val number: Int,
    val classes: List<String>,
    val include: Boolean
) : FunctionalTestBucket {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getString("subproject"),
        jsonObject.getIntValue("number"),
        jsonObject.getJSONArray("classes").map { it.toString() },
        jsonObject.getBoolean("include")
    )

    override fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): BuildTypeBucket = LargeSubprojectSplitBucket(
        gradleSubprojectProvider.getSubprojectByName(subproject)!!,
        number,
        include,
        classes.map { TestClassAndSourceSet(it) }
    )
}

data class MultipleSubprojectsFunctionalTestBucket(
    val subprojects: List<String>,
    val enableTD: Boolean
) : FunctionalTestBucket {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getJSONArray("subprojects").map { it.toString() },
        jsonObject.getBoolean("enableTD")
    )

    override fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): BuildTypeBucket {
        return SmallSubprojectBucket(
            subprojects.map { gradleSubprojectProvider.getSubprojectByName(it)!! },
            enableTD
        )
    }
}

fun BuildTypeBucket.toJsonBucket(): FunctionalTestBucket {
    return when (this) {
        is SmallSubprojectBucket -> MultipleSubprojectsFunctionalTestBucket(subprojects.map { it.name }, enableTestDistribution)
        is LargeSubprojectSplitBucket -> FunctionalTestBucketWithSplitClasses(subproject.name, number, classes.map { it.toPropertiesLine() }, include)
        else -> throw IllegalStateException("Unsupported type: ${this.javaClass}")
    }
}

class SubprojectTestClassTime(
    val subProject: GradleSubproject,
    val testClassTimes: List<TestClassTime> = emptyList()
) {
    val totalTime: Int = testClassTimes.sumOf { it.buildTimeMs }

    fun split(expectedBucketNumber: Int, enableTestDistribution: Boolean = false): List<BuildTypeBucket> {
        return if (expectedBucketNumber == 1) {
            listOf(
                SmallSubprojectBucket(
                    listOf(subProject),
                    enableTestDistribution
                )
            )
        } else {
            // fun <T, R> split(list: LinkedList<T>, toIntFunction: (T) -> Int, largeElementSplitFunction: (T, Int) -> List<R>, smallElementAggregateFunction: (List<T>) -> R, expectedBucketNumber: Int, maxNumberInBucket: Int): List<R> {
            // T TestClassTime
            // R List<TestClassTime>
            val list = LinkedList(testClassTimes.sortedBy { -it.buildTimeMs })
            val toIntFunction = TestClassTime::buildTimeMs
            val largeElementSplitFunction: (TestClassTime, Int) -> List<List<TestClassTime>> = { testClassTime: TestClassTime, _: Int -> listOf(listOf(testClassTime)) }
            val smallElementAggregateFunction: (List<TestClassTime>) -> List<TestClassTime> = { it }

            val buckets: List<List<TestClassTime>> = splitIntoBuckets(list, toIntFunction, largeElementSplitFunction, smallElementAggregateFunction, expectedBucketNumber, Integer.MAX_VALUE)

            buckets.mapIndexed { index: Int, classesInBucket: List<TestClassTime> ->
                val include = index != buckets.size - 1
                val classes = if (include) classesInBucket else buckets.subList(0, buckets.size - 1).flatten()
                LargeSubprojectSplitBucket(subProject, index + 1, include, classes.map { it.testClassAndSourceSet })
            }
        }
    }

    override fun toString(): String {
        return "SubprojectTestClassTime(subProject=${subProject.name}, totalTime=$totalTime)"
    }
}

class FunctionalTestBucketGenerator(private val model: CIBuildModel, testTimeDataJson: File) {
    private val buckets: Map<TestCoverage, List<BuildTypeBucket>> = buildBuckets(testTimeDataJson, model)

    fun generate(jsonFile: File) {
        jsonFile.writeText(
            JSON.toJSONString(
                buckets.map {
                    TestCoverageAndBucketSplits(it.key.uuid, it.value.map { it.toJsonBucket() })
                },
                SerializerFeature.PrettyFormat
            )
        )
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
                if (testCoverage.testType !in listOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion, TestType.soak)) {
                    result[testCoverage] = splitBucketsByTestClassesForBuildProject(testCoverage, stage, buildProjectClassTimes)
                }
            }
        }
        return result
    }

    private
    fun splitBucketsByTestClassesForBuildProject(testCoverage: TestCoverage, stage: Stage, buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes): List<BuildTypeBucket> {
        val validSubprojects = model.subprojects.getSubprojectsFor(testCoverage, stage)

        // Build project not found, don't split into buckets
        val subProjectToClassTimes: MutableMap<String, List<TestClassTime>> =
            determineSubProjectClassTimes(testCoverage, buildProjectClassTimes)?.toMutableMap() ?: return validSubprojects.map { SmallSubprojectBucket(it, false) }

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

        // We manually split docs subproject
        // `WatchedDirectoriesFileSystemWatchingIntegrationTest fails on local filesystem` doesn't work in remote executor
        return when {
            testCoverage.testType == TestType.platform && testCoverage.os == Os.LINUX ->
                splitDocsSubproject(validSubprojects) +
                    SmallSubprojectBucket(validSubprojects.first { it.name == "file-watching" }, false) +
                    splitIntoBuckets(validSubprojects, subProjectTestClassTimes, testCoverage, listOf("docs", "file-watching"), true)
            testCoverage.testType == TestType.platform ->
                splitDocsSubproject(validSubprojects) +
                    splitIntoBuckets(validSubprojects, subProjectTestClassTimes, testCoverage, listOf("docs"), false)
            testCoverage.os == Os.LINUX ->
                splitIntoBuckets(validSubprojects, subProjectTestClassTimes, testCoverage, listOf("file-watching"), true) +
                    SmallSubprojectBucket(validSubprojects.first { it.name == "file-watching" }, false)
            else ->
                splitIntoBuckets(validSubprojects, subProjectTestClassTimes, testCoverage, listOf("file-watching"), false)
        }
    }

    // docs subproject is special
    private fun splitDocsSubproject(allSubprojects: List<GradleSubproject>): List<BuildTypeBucket> {
        val docs = allSubprojects.find { it.name == "docs" }!!
        val docs1 = LargeSubprojectSplitBucket(docs, 1, true, listOf(TestClassAndSourceSet("org.gradle.docs.samples.Bucket1SnippetsTest", "docsTest")))
        val docs2 = LargeSubprojectSplitBucket(docs, 2, true, listOf(TestClassAndSourceSet("org.gradle.docs.samples.Bucket2SnippetsTest", "docsTest")))
        val docs3 = LargeSubprojectSplitBucket(docs, 3, true, listOf(TestClassAndSourceSet("org.gradle.docs.samples.Bucket3SnippetsTest", "docsTest")))
        val docs4 = LargeSubprojectSplitBucket(
            docs, 4, false,
            listOf(
                TestClassAndSourceSet("org.gradle.docs.samples.Bucket1SnippetsTest", "docsTest"),
                TestClassAndSourceSet("org.gradle.docs.samples.Bucket2SnippetsTest", "docsTest"),
                TestClassAndSourceSet("org.gradle.docs.samples.Bucket3SnippetsTest", "docsTest")
            )
        )
        return listOf(docs1, docs2, docs3, docs4)
    }

    private fun splitIntoBuckets(
        validSubprojects: List<GradleSubproject>,
        subProjectTestClassTimes: List<SubprojectTestClassTime>,
        testCoverage: TestCoverage,
        excludedSubprojectNames: List<String> = listOf(),
        enableTestDistribution: Boolean = false
    ): List<BuildTypeBucket> {
        val specialSubprojects = validSubprojects.filter { excludedSubprojectNames.contains(it.name) }
        val otherSubProjectTestClassTimes = subProjectTestClassTimes.filter { !excludedSubprojectNames.contains(it.subProject.name) }
        return splitIntoBuckets(
            LinkedList(otherSubProjectTestClassTimes),
            SubprojectTestClassTime::totalTime,
            { largeElement: SubprojectTestClassTime, size: Int ->
                if (enableTestDistribution)
                    largeElement.split(1, enableTestDistribution)
                else
                    largeElement.split(size)
            },
            { list: List<SubprojectTestClassTime> -> SmallSubprojectBucket(list.map { it.subProject }, enableTestDistribution) },
            testCoverage.expectedBucketNumber - specialSubprojects.size,
            MAX_PROJECT_NUMBER_IN_BUCKET
        )
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
                    it.buildJvmVersion == testCoverage.buildJvmVersion
            }
            foundTestCoverage?.let {
                buildProjectClassTimes[it.asId(MASTER_CHECK_CONFIGURATION)]
            }?.also {
                println("No test statistics found for ${testCoverage.asName()} (${testCoverage.uuid}), re-using the data from ${foundTestCoverage.asName()} (${foundTestCoverage.uuid})")
            }
        }
    }
}
