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
import configurations.CompileAll
import configurations.FunctionalTest
import configurations.GRADLE_RUNNER_STEP_NAME
import jetbrains.buildServer.configs.kotlin.DslContext
import model.CIBuildModel
import model.JsonBasedGradleSubprojectProvider
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class BuildTypeTest {
    init {
        DslContext.initForTest()
    }

    private
    val buildModel = CIBuildModel(
        projectId = "Gradle_Check",
        branch = VersionedSettingsBranch("master"),
        buildScanTags = listOf("Check"),
        subprojects = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json"))
    )

    @Test
    fun `CompileAll parameters are correct`() {
        val gradleStep = CompileAll(buildModel, buildModel.stages[0]).steps.getGradleStep(GRADLE_RUNNER_STEP_NAME)
        assertEquals(
            listOf(
                "-Dorg.gradle.workers.max=%maxParallelForks%",
                "-PmaxParallelForks=%maxParallelForks%",
                "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%",
                "-s",
                "%additional.gradle.parameters%",
                "--continue",
                "-DbuildScan.PartOf=QuickFeedbackLinuxOnly,QuickFeedback,PullRequestFeedback,ReadyforNightly,ReadyforRelease",
                "-Dscan.tag.CompileAll",
                "-Porg.gradle.java.installations.auto-download=false",
                "-Dscan.tag.Check",
                "-PteamCityBuildId=%teamcity.build.id%",
                "\"-Porg.gradle.java.installations.paths=%linux.java7.oracle.64bit%,%linux.java8.oracle.64bit%,%linux.java11.openjdk.64bit%,%linux.java17.openjdk.64bit%,%linux.java21.openjdk.64bit%,%linux.java23.openjdk.64bit%\"",
                "-Porg.gradle.java.installations.auto-download=false",
                "-Porg.gradle.java.installations.auto-detect=false"
            ).joinToString(" "),
            gradleStep.gradleParams
        )
    }

    @Test
    fun `functional test parameters are correct`() {
        val functionalTest = FunctionalTest(
            buildModel,
            "TestFunctionalTest",
            "Test Functional Test",
            "Test Functional Test",
            TestCoverage(4, TestType.platform, Os.WINDOWS, JvmVersion.java23, JvmVendor.openjdk),
            buildModel.stages[2]
        )
        val gradleStep = functionalTest.steps.getGradleStep(GRADLE_RUNNER_STEP_NAME)
        assertEquals(
            listOf(
                "-Dorg.gradle.workers.max=4",
                "-PmaxParallelForks=4",
                "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%",
                "-s",
                "%additional.gradle.parameters%",
                "--continue",
                "-DbuildScan.PartOf=PlatformJava23AdoptiumWindowsAmd64,PullRequestFeedback,ReadyforNightly,ReadyforRelease",
                "-PtestJavaVersion=23",
                "-PtestJavaVendor=openjdk",
                "-Dscan.tag.FunctionalTest",
                "-Dscan.value.coverageOs=windows",
                "-Dscan.value.coverageArch=amd64",
                "-Dscan.value.coverageJvmVendor=openjdk",
                "-Dscan.value.coverageJvmVersion=java23",
                "-PflakyTests=exclude",
                "-Dscan.tag.Check",
                "-PteamCityBuildId=%teamcity.build.id%",
                "\"-Porg.gradle.java.installations.paths=%windows.java8.openjdk.64bit%,%windows.java11.openjdk.64bit%,%windows.java17.openjdk.64bit%,%windows.java21.openjdk.64bit%,%windows.java23.openjdk.64bit%\"",
                "-Porg.gradle.java.installations.auto-download=false",
                "-Porg.gradle.java.installations.auto-detect=false"
            ).joinToString(" "),
            gradleStep.gradleParams
        )
    }
}
