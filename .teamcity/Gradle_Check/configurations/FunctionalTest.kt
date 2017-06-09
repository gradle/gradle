package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel
import model.OS
import model.TestCoverage
import model.TestType

class FunctionalTest(testCoverage : TestCoverage, bucket: Int = 0) : BuildType({
    uuid = "${testCoverage.asId()}_$bucket"
    extId = uuid
    name = testCoverage.asName() + if (bucket > 0) " ($bucket)" else ""
    if (bucket > 0) {
        description = CIBuildModel.testBuckets[bucket - 1].joinToString()
    }

    val quickTest = testCoverage.testType == TestType.quick
    applyDefaults(this, "${testCoverage.testType}Test$bucket", requiresDistribution = !quickTest,
            runsOnWindows = testCoverage.os == OS.windows, timeout = if (quickTest) 60 else 180)

    params {
        param("env.JAVA_HOME", "%${testCoverage.os}.${testCoverage.version}.${testCoverage.vendor}.64bit%")
    }
})
