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

import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.model.JsonBasedGradleSubprojectProvider
import Gradle_Check.model.PerformanceTestCoverage
import common.JvmVendor
import common.JvmVersion
import common.Os
import configurations.BaseGradleBuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import model.CIBuildModel
import model.PerformanceTestType
import model.SpecificBuild
import model.Stage
import model.StageNames
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class PerformanceTestBuildTypeTest {
    private
    val buildModel = CIBuildModel(buildScanTags = listOf("Check"), subprojects = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json")))

    @Test
    fun `create correct PerformanceTest build type`() {
        val performanceTest = PerformanceTest(buildModel, Stage(StageNames.READY_FOR_MERGE,
                specificBuilds = listOf(
                    SpecificBuild.BuildDistributions,
                    SpecificBuild.Gradleception,
                    SpecificBuild.SmokeTestsMinJavaVersion),
                functionalTests = listOf(
                    TestCoverage(1, TestType.platform, Os.LINUX, JvmVersion.java8),
                    TestCoverage(2, TestType.platform, Os.WINDOWS, JvmVersion.java11, vendor = JvmVendor.openjdk)),
                performanceTests = listOf(PerformanceTestCoverage(1, PerformanceTestType.test, Os.LINUX)),
                omitsSlowProjects = true),
            PerformanceTestCoverage(1, PerformanceTestType.test, Os.LINUX),
            "Description",
            "performance",
            listOf("largeTestProject", "smallTestProject"),
            2,
            extraParameters = "extraParameters"
        )

        assertEquals(listOf(
            "KILL_GRADLE_PROCESSES",
            "GRADLE_RUNNER",
            "CHECK_CLEAN_M2"
        ), performanceTest.steps.items.map(BuildStep::name))

        val expectedRunnerParams = listOf(
            "-PperformanceBaselines=%performance.baselines%",
            "\"-PtestJavaHome=%linux.java8.oracle.64bit%\"",
            "\"-PtestJavaVersion=8\"",
            "-Porg.gradle.java.installations.auto-detect=false",
            "-Porg.gradle.java.installations.auto-download=false",
            "\"-Porg.gradle.java.installations.paths=%linux.java11.openjdk.64bit%,%linux.java8.oracle.64bit%\"",
            "\"-Porg.gradle.performance.branchName=%teamcity.build.branch%\"",
            "\"-Porg.gradle.performance.db.url=%performance.db.url%\"",
            "\"-Porg.gradle.performance.db.username=%performance.db.username%\"",
            "\"-Porg.gradle.performance.db.password=%performance.db.password.tcagent%\"",
            "\"-PteamCityToken=%teamcity.user.bot-gradle.token%\"",
            "-Dorg.gradle.workers.max=%maxParallelForks%",
            "-PmaxParallelForks=%maxParallelForks%",
            "-s",
            "--daemon",
            "",
            "\"-Dscan.tag.PerformanceTest\"",
            "\"-Dgradle.cache.remote.url=%gradle.cache.remote.url%\"",
            "\"-Dgradle.cache.remote.username=%gradle.cache.remote.username%\"",
            "\"-Dgradle.cache.remote.password=%gradle.cache.remote.password%\""
        )

        assertEquals(
            (listOf(
                "clean",
                ":performance:largeTestProjectPerformanceTest --channel %performance.channel% ",
                ":performance:smallTestProjectPerformanceTest --channel %performance.channel% ",
                "extraParameters"
            ) + expectedRunnerParams).joinToString(" "),
            performanceTest.getGradleStep("GRADLE_RUNNER").gradleParams!!.trim()
        )
        assertEquals(BuildStep.ExecutionMode.DEFAULT, performanceTest.getGradleStep("GRADLE_RUNNER").executionMode)
    }

    private
    fun BaseGradleBuildType.getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep
}
