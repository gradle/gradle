package model

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.serializer.SerializerFeature
import common.Os
import common.VersionedSettingsBranch
import java.io.File
import java.util.LinkedList

/**
 * Process test-class-data.json and generates test-buckets.json
 *
 * Usage: mvn exec:java -Dexec.mainClass=model.FunctionalTestBucketGeneratorKt -Dexec.args='[input-test-class-data.json path] ./test-buckets.json'
 *
 */
fun main(args: Array<String>) {
    val model = CIBuildModel(
        projectId = "Check",
        branch = VersionedSettingsBranch("master", true),
        buildScanTags = listOf("Check"),
        subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json"))
    )
    val testClassDataJson = File(args[0])
    val generatedBucketsJson = File(args[1])

    FunctionalTestBucketGenerator(model, testClassDataJson).generate(generatedBucketsJson)
}

data class TestCoverageAndBucketSplits(
    val testCoverageUuid: Int,
    val buckets: List<FunctionalTestBucket>
)

interface FunctionalTestBucket

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

    fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): BuildTypeBucket = LargeSubprojectSplitBucket(
        gradleSubprojectProvider.getSubprojectByName(subproject)!!,
        number,
        include,
        classes.map { TestClassTime(it) }
    )
}

data class MultipleSubprojectsFunctionalTestBucket(
    val subprojects: List<String>,
    val enableTD: Boolean = false
) : FunctionalTestBucket {
    constructor(jsonObject: JSONObject) : this(
        jsonObject.getJSONArray("subprojects").map { it.toString() },
        jsonObject.getBoolean("enableTD")
    )

    fun toBuildTypeBucket(gradleSubprojectProvider: GradleSubprojectProvider): BuildTypeBucket {
        return SmallSubprojectBucket(
            subprojects.map { SubprojectTestClassTime(gradleSubprojectProvider.getSubprojectByName(it)!!) },
            enableTD
        )
    }
}

fun BuildTypeBucket.toJsonBucket(): FunctionalTestBucket {
    return when (this) {
        is GradleSubproject -> MultipleSubprojectsFunctionalTestBucket(listOf(name))
        is SmallSubprojectBucket -> MultipleSubprojectsFunctionalTestBucket(subprojectsBuildTime.map { it.subProject.name }, enableTestDistribution)
        is LargeSubprojectSplitBucket -> FunctionalTestBucketWithSplitClasses(subproject.name, number, classes.map { it.toPropertiesLine() }, include)
        else -> throw IllegalStateException("Unsupported type: ${this.javaClass}")
    }
}

class FunctionalTestBucketGenerator(private val model: CIBuildModel, testTimeDataJson: File) {
    private val buckets: Map<TestCoverage, List<BuildTypeBucket>> = buildBuckets(testTimeDataJson, model)

    fun generate(jsonFile: File) {
        jsonFile.writeText(JSON.toJSONString(buckets.map {
            TestCoverageAndBucketSplits(it.key.uuid, it.value.map { it.toJsonBucket() })
        }, SerializerFeature.PrettyFormat))
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
                if (testCoverage.testType !in listOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion)) {
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
        val subProjectToClassTimes: MutableMap<String, List<TestClassTime>> = determineSubProjectClassTimes(testCoverage, buildProjectClassTimes)?.toMutableMap() ?: return validSubprojects

        validSubprojects.forEach {
            if (!subProjectToClassTimes.containsKey(it.name)) {
                subProjectToClassTimes[it.name] = emptyList()
            }
        }

        val subProjectTestClassTimes: List<SubprojectTestClassTime> = subProjectToClassTimes
            .entries
            .filter { "UNKNOWN" != it.key }
            .filter { model.subprojects.getSubprojectByName(it.key) != null }
            .map { SubprojectTestClassTime(model.subprojects.getSubprojectByName(it.key)!!, it.value.filter { it.sourceSet != "test" }) }
            .sortedBy { -it.totalTime }

        return when {
            testCoverage.testType == TestType.platform && testCoverage.os == Os.WINDOWS -> {
                splitDocsSubproject(validSubprojects) + splitIntoBucketsExcludingSpecialBuckets(listOf("docs"), validSubprojects, subProjectTestClassTimes, testCoverage)
            }
            testCoverage.os == Os.LINUX -> {
                splitIntoBuckets(
                    LinkedList(subProjectTestClassTimes),
                    SubprojectTestClassTime::totalTime,
                    { largeElement: SubprojectTestClassTime, _: Int -> largeElement.split(1, true) },
                    { list: List<SubprojectTestClassTime> -> SmallSubprojectBucket(list) },
                    testCoverage.expectedBucketNumber,
                    MAX_PROJECT_NUMBER_IN_BUCKET
                )
            }
            else -> {
                splitIntoBuckets(
                    LinkedList(subProjectTestClassTimes),
                    SubprojectTestClassTime::totalTime,
                    { largeElement: SubprojectTestClassTime, size: Int -> largeElement.split(size) },
                    { list: List<SubprojectTestClassTime> -> SmallSubprojectBucket(list) },
                    testCoverage.expectedBucketNumber,
                    MAX_PROJECT_NUMBER_IN_BUCKET
                )
            }
        }
    }

    // docs subproject is special
    private fun splitDocsSubproject(allSubprojects: List<GradleSubproject>): List<BuildTypeBucket> {
        val docs = allSubprojects.find { it.name == "docs" }!!
        val docs1 = LargeSubprojectSplitBucket(docs, 1, true, listOf(TestClassTime("org.gradle.docs.samples.Bucket1SnippetsTest", "docsTest", -1)))
        val docs2 = LargeSubprojectSplitBucket(docs, 2, true, listOf(TestClassTime("org.gradle.docs.samples.Bucket2SnippetsTest", "docsTest", -1)))
        val docs3 = LargeSubprojectSplitBucket(docs, 3, true, listOf(TestClassTime("org.gradle.docs.samples.Bucket3SnippetsTest", "docsTest", -1)))
        val docs4 = LargeSubprojectSplitBucket(
            docs, 4, false, listOf(
                TestClassTime("org.gradle.docs.samples.Bucket1SnippetsTest", "docsTest", -1),
                TestClassTime("org.gradle.docs.samples.Bucket2SnippetsTest", "docsTest", -1),
                TestClassTime("org.gradle.docs.samples.Bucket3SnippetsTest", "docsTest", -1)
            )
        )
        return listOf(docs1, docs2, docs3, docs4)
    }

    private fun splitIntoBucketsExcludingSpecialBuckets(
        specialSubprojectNames: List<String>,
        validSubprojects: List<GradleSubproject>,
        subProjectTestClassTimes: List<SubprojectTestClassTime>,
        testCoverage: TestCoverage
    ): List<BuildTypeBucket> {
        val specialSubprojects = validSubprojects.filter { specialSubprojectNames.contains(it.name) }
        val otherSubProjectTestClassTimes = subProjectTestClassTimes.filter { !specialSubprojectNames.contains(it.subProject.name) }
        return splitIntoBuckets(
            LinkedList(otherSubProjectTestClassTimes),
            SubprojectTestClassTime::totalTime,
            { largeElement: SubprojectTestClassTime, size: Int -> largeElement.split(size) },
            { list: List<SubprojectTestClassTime> -> SmallSubprojectBucket(list) },
            testCoverage.expectedBucketNumber - specialSubprojects.size,
            MAX_PROJECT_NUMBER_IN_BUCKET
        )
    }

    private fun determineSubProjectClassTimes(testCoverage: TestCoverage, buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes): Map<String, List<TestClassTime>>? {
        val testCoverageId = testCoverage.asId("Gradle_Master_Check")
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
                buildProjectClassTimes[it.asId("Gradle_Check")]
            }?.also {
                println("No test statistics found for ${testCoverage.asName()} (${testCoverage.uuid}), re-using the data from ${foundTestCoverage.asName()} (${foundTestCoverage.uuid})")
            }
        }
    }
}
