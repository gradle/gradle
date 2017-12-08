package configurations

import model.CIBuildModel
import model.OS
import model.TestCoverage
import model.TestType

class FunctionalTest(model: CIBuildModel, testCoverage: TestCoverage, subProject: String = "") : BaseGradleBuildType(model, {
    uuid = testCoverage.asConfigurationId(model, subProject)
    extId = uuid
    name = testCoverage.asName() + if (!subProject.isEmpty()) " ($subProject)" else ""
    val testTask = if (!subProject.isEmpty()) {
        subProject + ":"
    } else {
        ""
    } + testCoverage.testType.name + "Test"
    val quickTest = testCoverage.testType == TestType.quick
    applyDefaults(model, this, testTask, notQuick = !quickTest, os = testCoverage.os,
            extraParameters = """"-PtestJavaHome=%${testCoverage.os}.${testCoverage.version}.${testCoverage.vendor}.64bit%"""",
            timeout = if (quickTest) 60 else 180)

    params {
        param("env.JAVA_HOME", "%${testCoverage.os}.java8.oracle.64bit%")
        if (testCoverage.os == OS.linux) {
            param("env.ANDROID_HOME", "/opt/android/sdk")
        }
    }
})
