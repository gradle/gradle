package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel.testBuckets
import model.TestCoverage

class FunctionalTestProject(testConfig : TestCoverage) : Project({
    this.uuid = testConfig.asId()
    this.extId = uuid
    this.name = testConfig.asName()

    (1..testBuckets.size).forEach { bucket ->
        buildType(FunctionalTest(testConfig, bucket))
    }
})
