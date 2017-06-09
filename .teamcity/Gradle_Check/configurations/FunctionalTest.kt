package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.OS
import model.TestCoverage
import model.TestType

class FunctionalTest(testCoverage : TestCoverage, bucket: Int = 0) : BuildType({
    uuid = "${testCoverage.asId()}_$bucket"
    extId = uuid
    name = "${testCoverage.asName()} ($bucket)"

    val quickTest = testCoverage.testType == TestType.quick
    applyDefaults(this, "${testCoverage.testType}Test$bucket", requiresDistribution = !quickTest,
            runsOnWindows = testCoverage.os == OS.windows, timeout = if (quickTest) 60 else 180)

    params {
        param("env.JAVA_HOME", "%${testCoverage.os}.${testCoverage.version}.${testCoverage.vendor}.64bit%")
    }
})
