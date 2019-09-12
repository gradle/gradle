package configurations

import common.Os
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel
import model.Stage
import model.TestCoverage
import model.TestType

class FunctionalTest(model: CIBuildModel, testCoverage: TestCoverage, subProjects: List<String> = listOf(), stage: Stage, buildTypeName: String = "", extraParameters: String = "") : BaseGradleBuildType(model, stage = stage, init = {
    uuid = testCoverage.asConfigurationId(model, buildTypeName)
    id = AbsoluteId(uuid)
    name = testCoverage.asName() + if (buildTypeName.isEmpty()) "" else " ($buildTypeName)"
    description = "${testCoverage.asName()} for ${when (subProjects.size) {
        0 -> "all projects "
        1 -> "project ${if (buildTypeName.isEmpty()) subProjects[0] else buildTypeName}"
        else -> "projects ${subProjects.joinToString(", ")}"
    }}"
    val testTaskName = "${testCoverage.testType.name}Test"
    val testTasks = if (subProjects.isEmpty())
        testTaskName
    else
        subProjects.map { "$it:$testTaskName" }.joinToString(" ")
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
