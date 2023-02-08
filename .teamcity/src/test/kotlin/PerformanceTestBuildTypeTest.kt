/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import common.JvmVendor
import common.JvmVersion
import common.Os
import common.VersionedSettingsBranch
import common.pluginPortalUrlOverride
import configurations.BaseGradleBuildType
import configurations.PerformanceTest
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import model.CIBuildModel
import model.JsonBasedGradleSubprojectProvider
import model.PerformanceTestCoverage
import model.PerformanceTestType
import model.SpecificBuild
import model.Stage
import model.StageName
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class PerformanceTestBuildTypeTest {
    init {
        // Set the project id here, so we can use methods on the DslContext
        DslContext.projectId = AbsoluteId("Gradle_Master")
        DslContext.addParameters("Branch" to "master")
    }

    private
    val buildModel = CIBuildModel(
        projectId = "Gradle_Check",
        branch = VersionedSettingsBranch("master"),
        buildScanTags = listOf("Check"),
        subprojects = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json"))
    )

    @Test
    fun `create correct PerformanceTest build type`() {
        val performanceTest = PerformanceTest(
            buildModel,
            Stage(
                StageName.PULL_REQUEST_FEEDBACK,
                specificBuilds = listOf(
                    SpecificBuild.BuildDistributions,
                    SpecificBuild.Gradleception,
                    SpecificBuild.SmokeTestsMinJavaVersion
                ),
                functionalTests = listOf(
                    TestCoverage(1, TestType.platform, Os.LINUX, JvmVersion.java8),
                    TestCoverage(2, TestType.platform, Os.WINDOWS, JvmVersion.java11, vendor = JvmVendor.openjdk)
                ),
                performanceTests = listOf(PerformanceTestCoverage(1, PerformanceTestType.per_commit, Os.LINUX))
            ),
            PerformanceTestCoverage(1, PerformanceTestType.per_commit, Os.LINUX),
            "Description",
            "performance",
            listOf("largeTestProject", "smallTestProject"),
            2,
            extraParameters = "extraParameters"
        )

        assertEquals(
            listOf(
                "KILL_GRADLE_PROCESSES",
                "GRADLE_RUNNER",
                "CHECK_CLEAN_M2_ANDROID_USER_HOME"
            ),
            performanceTest.steps.items.map(BuildStep::name)
        )

        val expectedRunnerParams = listOf(
            "-PperformanceBaselines=%performance.baselines%",
            "-PtestJavaVersion=11",
            "-PtestJavaVendor=openjdk",
            "-PautoDownloadAndroidStudio=true",
            "-PrunAndroidStudioInHeadlessMode=true",
            "-Porg.gradle.java.installations.auto-download=false",
            "\"-Porg.gradle.java.installations.paths=%linux.java8.oracle.64bit%,%linux.java11.openjdk.64bit%,%linux.java17.openjdk.64bit%,%linux.java19.openjdk.64bit%,%linux.java8.openjdk.64bit%\"",
            "\"-Porg.gradle.performance.branchName=%teamcity.build.branch%\"",
            "\"-Porg.gradle.performance.db.url=%performance.db.url%\"",
            "\"-Porg.gradle.performance.db.username=%performance.db.username%\"",
            "-DenableTestDistribution=%enableTestDistribution%",
            "-Dorg.gradle.workers.max=%maxParallelForks%",
            "-PmaxParallelForks=%maxParallelForks%",
            pluginPortalUrlOverride,
            "-s",
            "--no-configuration-cache",
            "%additional.gradle.parameters%",
            "--daemon",
            "--continue",
            "\"-Dscan.tag.PerformanceTest\""
        )

        assertEquals(
            (
                listOf(
                    "clean",
                    ":performance:largeTestProjectPerformanceTest --channel %performance.channel% ",
                    ":performance:smallTestProjectPerformanceTest --channel %performance.channel% ",
                    "extraParameters"
                ) + expectedRunnerParams
                ).joinToString(" "),
            performanceTest.getGradleStep("GRADLE_RUNNER").gradleParams!!.trim()
        )
        assertEquals(BuildStep.ExecutionMode.DEFAULT, performanceTest.getGradleStep("GRADLE_RUNNER").executionMode)
    }

    private
    fun BaseGradleBuildType.getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep
}
