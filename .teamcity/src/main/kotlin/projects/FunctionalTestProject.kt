package projects

import configurations.BaseGradleBuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.FunctionalTestBucketProvider
import model.Stage
import model.TestCoverage

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
    val functionalTests: List<BaseGradleBuildType> = functionalTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)
    init {
        functionalTests.forEach(this::buildType)
    }
}
