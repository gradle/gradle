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

import common.Os
import common.PLUGINS_PORTAL_URL_OVERRIDE
import common.VersionedSettingsBranch
import configurations.BaseGradleBuildType
import configurations.PerformanceTest
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.buildSteps.GradleBuildStep
import model.CIBuildModel
import model.JsonBasedGradleSubprojectProvider
import model.PerformanceTestCoverage
import model.PerformanceTestType
import model.Stage
import model.StageName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class PerformanceTestBuildTypeTest {
    init {
        DslContext.initForTest()
    }

    private val buildModel =
        CIBuildModel(
            projectId = "Gradle_Check",
            branch = VersionedSettingsBranch("master"),
            buildScanTags = listOf("Check"),
            subprojects = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json")),
        )

    @Test
    fun `create correct PerformanceTest build type for Linux`() {
        val performanceTest =
            PerformanceTest(
                buildModel,
                Stage(
                    StageName.PULL_REQUEST_FEEDBACK,
                    performanceTests = listOf(PerformanceTestCoverage(1, PerformanceTestType.PER_COMMIT, Os.LINUX)),
                ),
                PerformanceTestCoverage(1, PerformanceTestType.PER_COMMIT, Os.LINUX),
                "Description",
                "performance",
                listOf("largeTestProject", "smallTestProject"),
                2,
                extraParameters = "extraParameters",
            )

        assertEquals(
            listOf(
                "EC2_BUILD_CUSTOMIZATIONS",
                "KILL_ALL_GRADLE_PROCESSES",
                "GRADLE_RUNNER",
                "KILL_PROCESSES_STARTED_BY_GRADLE",
                "CHECK_CLEAN_M2_ANDROID_USER_HOME",
            ),
            performanceTest.steps.items.map(BuildStep::name),
        )
        val linuxPaths =
            listOf(
                "%linux.java7.oracle.64bit%",
                "%linux.java8.oracle.64bit%",
                "%linux.java11.openjdk.64bit%",
                "%linux.java17.openjdk.64bit%",
                "%linux.java21.openjdk.64bit%",
                "%linux.java23.openjdk.64bit%",
                "%linux.java24.openjdk.64bit%",
            )
        val expectedInstallationPaths = linuxPaths.joinToString(",")
        val expectedRunnerParams =
            listOf(
                "-PperformanceBaselines=%performance.baselines%",
                "-PtestJavaVersion=17",
                "-PtestJavaVendor=openjdk",
                "-PautoDownloadAndroidStudio=true",
                "-PrunAndroidStudioInHeadlessMode=true",
                "-Porg.gradle.java.installations.auto-download=false",
                "\"-Porg.gradle.java.installations.paths=$expectedInstallationPaths\"",
                "\"-Porg.gradle.performance.branchName=%teamcity.build.branch%\"",
                "\"-Porg.gradle.performance.db.url=%performance.db.url%\"",
                "\"-Porg.gradle.performance.db.username=%performance.db.username%\"",
                "-DenableTestDistribution=%enableTestDistribution%",
                "-Dorg.gradle.workers.max=%maxParallelForks%",
                "-PmaxParallelForks=%maxParallelForks%",
                PLUGINS_PORTAL_URL_OVERRIDE,
                "-s",
                "%additional.gradle.parameters%",
                "--continue",
                "-DbuildScan.PartOf=PullRequestFeedback,ReadyforNightly,ReadyforRelease",
                "-Dscan.tag.PerformanceTest",
            )

        assertEquals(
            (
                listOf(
                    "clean",
                    ":performance:largeTestProjectPerformanceTest",
                    ":performance:smallTestProjectPerformanceTest",
                    "extraParameters",
                ) + expectedRunnerParams
            ).joinToString(" "),
            performanceTest.getGradleStep("GRADLE_RUNNER").gradleParams!!.trim(),
        )
        assertEquals(BuildStep.ExecutionMode.DEFAULT, performanceTest.getGradleStep("GRADLE_RUNNER").executionMode)
    }

    @Test
    fun `create correct PerformanceTest build type for Windows`() {
        val performanceTest =
            PerformanceTest(
                buildModel,
                Stage(
                    StageName.PULL_REQUEST_FEEDBACK,
                    performanceTests = listOf(PerformanceTestCoverage(2, PerformanceTestType.PER_COMMIT, Os.WINDOWS)),
                ),
                PerformanceTestCoverage(2, PerformanceTestType.PER_COMMIT, Os.WINDOWS),
                "Description",
                "performance",
                listOf("largeTestProject", "smallTestProject"),
                2,
                extraParameters = "extraParameters",
            )

        assertEquals(
            listOf(
                "KILL_ALL_GRADLE_PROCESSES",
                "SETUP_VIRTUAL_DISK_FOR_PERF_TEST",
                "GRADLE_RUNNER",
                "REMOVE_VIRTUAL_DISK_FOR_PERF_TEST",
                "KILL_PROCESSES_STARTED_BY_GRADLE",
                "CHECK_CLEAN_M2_ANDROID_USER_HOME",
            ),
            performanceTest.steps.items.map(BuildStep::name),
        )
        val windowsPaths =
            listOf(
                "%windows.java8.openjdk.64bit%",
                "%windows.java11.openjdk.64bit%",
                "%windows.java17.openjdk.64bit%",
                "%windows.java21.openjdk.64bit%",
                "%windows.java23.openjdk.64bit%",
                "%windows.java24.openjdk.64bit%",
            )
        val expectedInstallationPaths = windowsPaths.joinToString(",")
        val expectedRunnerParams =
            listOf(
                "-PperformanceBaselines=%performance.baselines%",
                "-PtestJavaVersion=17",
                "-PtestJavaVendor=openjdk",
                "-PautoDownloadAndroidStudio=true",
                "-PrunAndroidStudioInHeadlessMode=true",
                "-Porg.gradle.java.installations.auto-download=false",
                "\"-Porg.gradle.java.installations.paths=$expectedInstallationPaths\"",
                "-Porg.gradle.performance.branchName=\"%teamcity.build.branch%\"",
                "-Porg.gradle.performance.db.url=\"%performance.db.url%\"",
                "-Porg.gradle.performance.db.username=\"%performance.db.username%\"",
                "-DenableTestDistribution=%enableTestDistribution%",
                "-Dorg.gradle.workers.max=%maxParallelForks%",
                "-PmaxParallelForks=%maxParallelForks%",
                PLUGINS_PORTAL_URL_OVERRIDE,
                "-s",
                "%additional.gradle.parameters%",
                "--continue",
                "-DbuildScan.PartOf=PullRequestFeedback,ReadyforNightly,ReadyforRelease",
                "-Dscan.tag.PerformanceTest",
            )

        assertEquals(
            (
                listOf(
                    "clean",
                    ":performance:largeTestProjectPerformanceTest",
                    ":performance:smallTestProjectPerformanceTest",
                    "extraParameters",
                ) + expectedRunnerParams
            ).joinToString(" "),
            performanceTest.getGradleStep("GRADLE_RUNNER").gradleParams!!.trim(),
        )
        assertEquals(BuildStep.ExecutionMode.DEFAULT, performanceTest.getGradleStep("GRADLE_RUNNER").executionMode)
    }

    private fun BaseGradleBuildType.getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep
}
