package projects

import common.Os
import common.compileAllDependency
import configurations.BaseGradleBuildType
import configurations.CompileAllProduction
import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.FunctionalTestBucketProvider
import model.Stage
import model.TestCoverage
import model.TestType
import model.getBucketUuid

const val DEFAULT_FUNCTIONAL_TEST_BUCKET_SIZE = 50
const val DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE = 20

class FunctionalTestProject(
    val model: CIBuildModel,
    functionalTestBucketProvider: FunctionalTestBucketProvider,
    val testCoverage: TestCoverage,
    val stage: Stage
) : Project({
    this.id(testCoverage.asId(model))
    this.name = testCoverage.asName()
}) {
    val functionalTests: List<BaseGradleBuildType> = functionalTestBucketProvider.createFunctionalTestsFor(stage, testCoverage).let {
        addDummyBuckets(it)
    }

    init {
        functionalTests.forEach(this::buildType)
    }

    /**
     * These build configurations are used to retain build histories. For example, we had 50 buckets before,
     * if we remove 30 of them, the histories are not accessible anymore. As a workaround, we don't remove
     * the 30 buckets but not dependencies of trigger build.
     */
    private fun addDummyBuckets(functionalTestBuckets: List<FunctionalTest>): List<BaseGradleBuildType> {
        if (testCoverage.os != Os.LINUX) {
            return functionalTestBuckets
        }
        if (testCoverage.testType == TestType.quickFeedbackCrossVersion ||
            testCoverage.testType == TestType.allVersionsCrossVersion ||
            testCoverage.testType == TestType.allVersionsIntegMultiVersion
        ) {
            return functionalTestBuckets
        }
        return functionalTestBuckets + (functionalTestBuckets.size until DEFAULT_FUNCTIONAL_TEST_BUCKET_SIZE).map {
            DummyFunctionalTest(
                model,
                testCoverage.getBucketUuid(model, it),
                "${testCoverage.asName()} (dummy bucket${it + 1})",
                "${testCoverage.asName()} (dummy bucket${it + 1})",
                stage
            )
        }
    }
}

class DummyFunctionalTest(model: CIBuildModel, id: String, name: String, description: String, stage: Stage) : BaseGradleBuildType(stage, init = {
    this.name = name
    this.description = description
    this.id(id)

    dependencies {
        compileAllDependency(CompileAllProduction.buildTypeId(model))
    }
})
