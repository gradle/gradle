import common.Os
import common.VersionedSettingsBranch
import common.pluginPortalUrlOverride
import configurations.BaseGradleBuildType
import configurations.applyDefaults
import configurations.applyTestDefaults
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import model.CIBuildModel
import model.JsonBasedGradleSubprojectProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

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

@ExtendWith(MockKExtension::class)
class ApplyDefaultConfigurationTest {
    @MockK(relaxed = true)
    lateinit var buildType: BaseGradleBuildType

    private
    val steps = BuildSteps()

    private
    val buildModel = CIBuildModel(
        projectId = "Gradle_Check",
        branch = VersionedSettingsBranch("master"),
        buildScanTags = listOf("Check"),
        subprojects = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json"))
    )

    @BeforeEach
    fun setUp() {
        val stepsCapturer = slot<BuildSteps.() -> Unit>()
        every {
            buildType.steps(capture(stepsCapturer))
        } answers {
            stepsCapturer.captured(steps)
            mockk()
        }
    }

    @Test
    fun `can apply defaults to configurations`() {
        applyDefaults(buildModel, buildType, "myTask")

        assertEquals(
            listOf(
                "KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS",
                "GRADLE_RUNNER",
                "CHECK_CLEAN_M2_ANDROID_USER_HOME"
            ),
            steps.items.map(BuildStep::name)
        )
        assertEquals(expectedRunnerParam(), getGradleStep("GRADLE_RUNNER").gradleParams)
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "myParam, true,  '--daemon'",
            "''     , true,  '--daemon'",
            "myParam, false, '--no-daemon'",
            "''     , false, '--no-daemon'"
        ]
    )
    fun `can apply defaults to linux test configurations`(extraParameters: String, daemon: Boolean, expectedDaemonParam: String) {
        applyTestDefaults(buildModel, buildType, "myTask", extraParameters = extraParameters, daemon = daemon)

        assertEquals(
            listOf(
                "KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS",
                "GRADLE_RUNNER",
                "KILL_PROCESSES_STARTED_BY_GRADLE",
                "CHECK_CLEAN_M2_ANDROID_USER_HOME"
            ),
            steps.items.map(BuildStep::name)
        )
        verifyGradleRunnerParams(extraParameters, expectedDaemonParam)
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "myParam, true,  '--daemon'",
            "''     , true,  '--daemon'",
            "myParam, false, '--no-daemon'",
            "''     , false, '--no-daemon'"
        ]
    )
    fun `can apply defaults to windows test configurations`(extraParameters: String, daemon: Boolean, expectedDaemonParam: String) {
        applyTestDefaults(buildModel, buildType, "myTask", os = Os.WINDOWS, extraParameters = extraParameters, daemon = daemon)

        assertEquals(
            listOf(
                "KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS",
                "CLEAN_UP_PERFORMANCE_BUILD_DIR",
                "GRADLE_RUNNER",
                "KILL_PROCESSES_STARTED_BY_GRADLE",
                "CHECK_CLEAN_M2_ANDROID_USER_HOME"
            ),
            steps.items.map(BuildStep::name)
        )
        verifyGradleRunnerParams(extraParameters, expectedDaemonParam, Os.WINDOWS)
    }

    private
    fun verifyGradleRunnerParams(extraParameters: String, expectedDaemonParam: String, os: Os = Os.LINUX) {
        assertEquals(BuildStep.ExecutionMode.DEFAULT, getGradleStep("GRADLE_RUNNER").executionMode)

        assertEquals(expectedRunnerParam(expectedDaemonParam, extraParameters, os), getGradleStep("GRADLE_RUNNER").gradleParams)
        assertEquals("clean myTask", getGradleStep("GRADLE_RUNNER").tasks)
    }

    private
    fun getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep

    private
    fun expectedRunnerParam(daemon: String = "--daemon", extraParameters: String = "", os: Os = Os.LINUX): String {
        val linuxPaths = "-Porg.gradle.java.installations.paths=%linux.java8.oracle.64bit%,%linux.java11.openjdk.64bit%,%linux.java17.openjdk.64bit%,%linux.java20.openjdk.64bit%,%linux.java8.openjdk.64bit%"
        val windowsPaths = "-Porg.gradle.java.installations.paths=%windows.java8.oracle.64bit%,%windows.java11.openjdk.64bit%,%windows.java17.openjdk.64bit%,%windows.java20.openjdk.64bit%,%windows.java8.openjdk.64bit%"
        val expectedInstallationPaths = if (os == Os.WINDOWS) windowsPaths else linuxPaths
        return "-Dorg.gradle.workers.max=%maxParallelForks% -PmaxParallelForks=%maxParallelForks% $pluginPortalUrlOverride -s --no-configuration-cache %additional.gradle.parameters% $daemon --continue $extraParameters \"-Dscan.tag.Check\" \"-Dscan.tag.\" -PteamCityBuildId=%teamcity.build.id% \"$expectedInstallationPaths\" -Porg.gradle.java.installations.auto-download=false"
    }
}
