package configurations

import common.Os
import common.functionalTestExtraParameters
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import model.CIBuildModel
import model.Stage
import model.TestCoverage
import model.TestType

class FunctionalTest(
    model: CIBuildModel,
    id: String,
    name: String,
    description: String,
    val testCoverage: TestCoverage,
    stage: Stage,
    subprojects: List<String> = listOf(),
    extraParameters: String = "",
    extraBuildSteps: BuildSteps.() -> Unit = {},
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(stage = stage, init = {
    this.name = name
    this.description = description
    this.id(id)
    val testTasks = getTestTaskName(testCoverage, subprojects)

    if (name.contains("(configuration-cache)")) {
        requirements {
            doesNotContain("teamcity.agent.name", "ec2")
            // US region agents have name "EC2-XXX"
            doesNotContain("teamcity.agent.name", "EC2")
        }
    }

    val enableTestDistribution = testCoverage.testDistribution

    applyTestDefaults(model, this, testTasks, notQuick = !testCoverage.isQuick, os = testCoverage.os,
        extraParameters = (
            listOf(functionalTestExtraParameters("FunctionalTest", testCoverage.os, testCoverage.testJvmVersion.major.toString(), testCoverage.vendor.name)) +
                if (enableExperimentalTestDistribution(testCoverage, subprojects)) "-DenableTestDistribution=%enableTestDistribution%" else "" +
                    extraParameters
            ).filter { it.isNotBlank() }.joinToString(separator = " "),
        timeout = testCoverage.testType.timeout,
        extraSteps = extraBuildSteps,
        preSteps = preBuildSteps)

    params {
        if (enableTestDistribution) {
            param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%e.grdev.net.access.key%")
        }

        if (testCoverage.testDistribution) {
            param("maxParallelForks", "16")
        }
    }

    if (testCoverage.testType == TestType.soak || testTasks.contains("plugins:")) {
        failureConditions {
            // JavaExecDebugIntegrationTest.debug session fails without debugger might cause JVM crash
            // Some soak tests produce OOM exceptions
            javaCrash = false
        }
    }
})

fun enableExperimentalTestDistribution(testCoverage: TestCoverage, subprojects: List<String>) = testCoverage.os == Os.LINUX && (subprojects == listOf("core") || subprojects == listOf("dependency-management"))

fun getTestTaskName(testCoverage: TestCoverage, subprojects: List<String>): String {
    val testTaskName = "${testCoverage.testType.name}Test"
    return when {
        testCoverage.testDistribution -> {
            return testTaskName
        }
        subprojects.isEmpty() -> {
            testTaskName
        }
        else -> {
            subprojects.joinToString(" ") { "$it:$testTaskName" }
        }
    }
}
