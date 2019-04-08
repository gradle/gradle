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
import configurations.BaseGradleBuildType
import configurations.PerformanceTestCoordinator
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.GradleBuildStep
import model.PerformanceTestType
import model.SpecificBuild
import model.Stage
import model.StageNames
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PerformanceTestBuildTypeTest {
    private
    val buildModel = model.CIBuildModel(buildScanTags = listOf("Check"))

    @Test
    fun `create correct PerformanceTest build type`() {
        val performanceTest = PerformanceTestCoordinator(buildModel, PerformanceTestType.test, Stage(StageNames.READY_FOR_MERGE,
                specificBuilds = listOf(
                        SpecificBuild.BuildDistributions,
                        SpecificBuild.Gradleception,
                        SpecificBuild.SmokeTests),
                functionalTests = listOf(
                        TestCoverage(1, TestType.platform, Os.linux, JvmVersion.java8),
                        TestCoverage(2, TestType.platform, Os.windows, JvmVersion.java11, vendor = JvmVendor.openjdk)),
                performanceTests = listOf(PerformanceTestType.test),
                omitsSlowProjects = true))

        assertEquals(listOf(
                "GRADLE_RUNNER",
                "CHECK_CLEAN_M2",
                "GRADLE_RERUNNER"
        ), performanceTest.steps.items.map(BuildStep::name))

        val expectedRunnerParams = listOf(
                "--baselines",
                "%performance.baselines%",
                "",
                "-x",
                "prepareSamples",
                "-Porg.gradle.performance.branchName=%teamcity.build.branch%",
                "-Porg.gradle.performance.db.url=%performance.db.url%",
                "-Porg.gradle.performance.db.username=%performance.db.username%",
                "-Porg.gradle.performance.db.password=%performance.db.password.tcagent%",
                "-PteamCityUsername=%teamcity.username.restbot%",
                "-PteamCityPassword=%teamcity.password.restbot%",
                "-PtestJavaHome=%linux.java8.oracle.64bit%",
                "-PmaxParallelForks=%maxParallelForks%",
                "-s",
                "--daemon",
                "",
                "-I",
                "\"%teamcity.build.checkoutDir%/gradle/init-scripts/build-scan.init.gradle.kts\"",
                "-Dorg.gradle.internal.tasks.createops",
                "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%",
                "-Porg.gradle.performance.buildTypeId=Gradle_Check_IndividualPerformanceScenarioWorkersLinux",
                "-Porg.gradle.performance.workerTestTaskName=fullPerformanceTest",
                "-Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id%",
                "-PgithubToken=%github.ci.oauth.token%",
                "\"-Dscan.tag.PerformanceTest\"",
                "--build-cache",
                "\"-Dgradle.cache.remote.url=%gradle.cache.remote.url%\"",
                "\"-Dgradle.cache.remote.username=%gradle.cache.remote.username%\"",
                "\"-Dgradle.cache.remote.password=%gradle.cache.remote.password%\""
        )

        assertEquals(
                (listOf("clean", "distributedPerformanceTests") + expectedRunnerParams).joinToString(" "),
                performanceTest.getGradleStep("GRADLE_RUNNER").gradleParams!!.trim()
        )
        assertEquals(BuildStep.ExecutionMode.DEFAULT, performanceTest.getGradleStep("GRADLE_RUNNER").executionMode)

        assertEquals(
                (listOf("tagBuild", "distributedPerformanceTests")
                        + expectedRunnerParams
                        + "-PteamCityBuildId=%teamcity.build.id%"
                        + "-PonlyPreviousFailedTestClasses=true"
                        + "-Dscan.tag.RERUN_TESTS"
                        ).joinToString(" "),
                performanceTest.getGradleStep("GRADLE_RERUNNER").gradleParams
        )
        assertEquals(BuildStep.ExecutionMode.RUN_ON_FAILURE, performanceTest.getGradleStep("GRADLE_RERUNNER").executionMode)
    }

    private
    fun BaseGradleBuildType.getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep
}
