package configurations

import common.Os
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel
import model.Stage
import model.TestCoverage
import model.TestType

class FunctionalTest(model: CIBuildModel, uuid: String, name: String, description: String, testCoverage: TestCoverage, stage: Stage, subProjects: List<String> = listOf(), extraParameters: String = "") : BaseGradleBuildType(model, stage = stage, init = {
    this.uuid = uuid
    this.name = name
    this.description = description
    id = AbsoluteId(uuid)
    val testTaskName = "${testCoverage.testType.name}Test"
    val testTasks = if (subProjects.isEmpty())
        testTaskName
    else
        subProjects.joinToString(" ") { "$it:$testTaskName" }
    val quickTest = testCoverage.testType == TestType.quick
    val buildScanTags = listOf("FunctionalTest")
    val buildScanValues = mapOf(
        "coverageOs" to testCoverage.os.name,
        "coverageJvmVendor" to testCoverage.vendor.name,
        "coverageJvmVersion" to testCoverage.testJvmVersion.name
    )
    applyTestDefaults(model, this, testTasks, notQuick = !quickTest, os = testCoverage.os,
        extraParameters = (
            listOf(""""-PtestJavaHome=%${testCoverage.os}.${testCoverage.testJvmVersion}.${testCoverage.vendor}.64bit%"""") +
                buildScanTags.map { buildScanTag(it) } +
                buildScanValues.map { buildScanCustomValue(it.key, it.value) } +
                extraParameters
            ).filter { it.isNotBlank() }.joinToString(separator = " "),
        timeout = testCoverage.testType.timeout)

    params {
        param("env.JAVA_HOME", "%${testCoverage.os}.${testCoverage.buildJvmVersion}.openjdk.64bit%")
        when (testCoverage.os) {
            Os.linux -> param("env.ANDROID_HOME", "/opt/android/sdk")
            // Use fewer parallel forks on macOs, since the agents are not very powerful.
            Os.macos -> param("maxParallelForks", "2")
            else -> {
            }
        }
    }
})
