package configurations

import model.CIBuildModel
import model.OS
import model.Stage
import model.TestCoverage
import model.TestType

class FunctionalTest(model: CIBuildModel, testCoverage: TestCoverage, subProject: String = "", stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = testCoverage.asConfigurationId(model, subProject)
    id = uuid
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
            "coverageJvmVersion" to testCoverage.version.name
    )
    applyDefaults(model, this, testTask, notQuick = !quickTest, os = testCoverage.os,
            extraParameters = (
                    listOf(""""-PtestJavaHome=%${testCoverage.os}.${testCoverage.version}.${testCoverage.vendor}.64bit%"""")
                            + buildScanTags.map { buildScanTag(it) }
                            + buildScanValues.map { buildScanCustomValue(it.key, it.value) }
                    ).joinToString(separator = " "),
            timeout = testCoverage.testType.timeout)

    params {
        param("env.JAVA_HOME", "%${testCoverage.os}.java8.oracle.64bit%")
        if (testCoverage.os == OS.linux) {
            param("env.ANDROID_HOME", "/opt/android/sdk")
        }
    }
})
