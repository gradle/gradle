package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.TestCoverage

class FunctionalTestProject(testConfig : TestCoverage) : Project({
    this.uuid = testConfig.asId()
    this.extId = uuid
    this.name = "Test Coverage - " + testConfig.asName()

    (1..CIBuildModel.testBucketCount).forEach { bucket ->
        buildType(FunctionalTest(testConfig, bucket))
    }
})
