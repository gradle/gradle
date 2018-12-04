package configurations_g4

import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import model_g4.CIBuildModel
import model_g4.OS
import model_g4.Stage
import model_g4.TestCoverage
import model_g4.TestType

class FunctionalTest(model: CIBuildModel, testCoverage: TestCoverage, subProject: String = "", useDaemon: Boolean = true, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = testCoverage.asConfigurationId(model, subProject)
    id = AbsoluteId(uuid)
    name = testCoverage.asName() + if (!subProject.isEmpty()) " ($subProject)" else ""
    val testTask = if (!subProject.isEmpty()) {
        subProject + ":"
    } else {
        ""
    } + testCoverage.testType.name + "Test"
    val quickTest = testCoverage.testType == TestType.quick
    val buildScanTags = listOf("FunctionalTest")
    val buildScanValues = mapOf(
            "coverageOs" to testCoverage.os.name,
            "coverageJvmVendor" to testCoverage.vendor.name,
            "coverageJvmVersion" to testCoverage.testJvmVersion.name
    )
    applyDefaults(model, this, testTask, notQuick = !quickTest, os = testCoverage.os,
            extraParameters = (
                    listOf(""""-PtestJavaHome=%${testCoverage.os}.${testCoverage.testJvmVersion}.${testCoverage.vendor}.64bit%"""")
                            + buildScanTags.map { buildScanTag(it) }
                            + buildScanValues.map { buildScanCustomValue(it.key, it.value) }
                    ).joinToString(separator = " "),
            timeout = testCoverage.testType.timeout,
            daemon = useDaemon)

    params {
        param("env.JAVA_HOME", "%${testCoverage.os}.${testCoverage.buildJvmVersion}.oracle.64bit%")
        if (testCoverage.os == OS.linux) {
            param("env.ANDROID_HOME", "/opt/android/sdk")
        }
    }
})
