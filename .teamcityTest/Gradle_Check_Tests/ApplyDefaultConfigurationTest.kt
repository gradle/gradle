import common.Os
import configurations.BaseGradleBuildType
import configurations.applyDefaults
import configurations.applyTestDefaults
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.GradleBuildStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
    val buildModel = model.CIBuildModel(buildScanTags = listOf("Check"))

    @BeforeEach
    fun setUp() {
        val stepsCapturer = slot<BuildSteps.() -> kotlin.Unit>()
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

        assertEquals(listOf(
            "GRADLE_RUNNER",
            "CHECK_CLEAN_M2",
            "VERIFY_TEST_FILES_CLEANUP",
            "TAG_BUILD"
        ), steps.items.map(BuildStep::name))
        assertEquals(expectedRunnerParam(), getGradleStep("GRADLE_RUNNER").gradleParams)
    }

    @ParameterizedTest
    @CsvSource(value = [
        "myParam, true,  '--daemon'",
        "''     , true,  '--daemon'",
        "myParam, false, '--no-daemon'",
        "''     , false, '--no-daemon'"
    ])
    fun `can apply defaults to linux test configurations`(extraParameters: String, daemon: Boolean, expectedDaemonParam: String) {
        applyTestDefaults(buildModel, buildType, "myTask", extraParameters = extraParameters, daemon = daemon)

        assertEquals(listOf(
            "GRADLE_RUNNER",
            "GRADLE_RERUNNER",
            "CHECK_CLEAN_M2",
            "VERIFY_TEST_FILES_CLEANUP",
            "TAG_BUILD"
        ), steps.items.map(BuildStep::name))
        verifyGradleRunnerParams(extraParameters, daemon, expectedDaemonParam)
    }

    @ParameterizedTest
    @CsvSource(value = [
        "myParam, true,  '--daemon'",
        "''     , true,  '--daemon'",
        "myParam, false, '--no-daemon'",
        "''     , false, '--no-daemon'"
    ])
    fun `can apply defaults to windows test configurations`(extraParameters: String, daemon: Boolean, expectedDaemonParam: String) {
        applyTestDefaults(buildModel, buildType, "myTask", os = Os.windows, extraParameters = extraParameters, daemon = daemon)

        assertEquals(listOf(
            "GRADLE_RUNNER",
            "KILL_PROCESSES_STARTED_BY_GRADLE",
            "GRADLE_RERUNNER",
            "KILL_PROCESSES_STARTED_BY_GRADLE_RERUN",
            "CHECK_CLEAN_M2",
            "VERIFY_TEST_FILES_CLEANUP",
            "TAG_BUILD"
        ), steps.items.map(BuildStep::name))
        verifyGradleRunnerParams(extraParameters, daemon, expectedDaemonParam)
    }

    private
    fun verifyGradleRunnerParams(extraParameters: String, daemon: Boolean, expectedDaemonParam: String) {
        assertEquals(BuildStep.ExecutionMode.DEFAULT, getGradleStep("GRADLE_RUNNER").executionMode)
        assertEquals(BuildStep.ExecutionMode.RUN_ON_FAILURE, getGradleStep("GRADLE_RERUNNER").executionMode)

        assertEquals(expectedRunnerParam(expectedDaemonParam, extraParameters), getGradleStep("GRADLE_RUNNER").gradleParams)
        assertEquals(expectedRunnerParam(expectedDaemonParam, extraParameters) + " -PonlyPreviousFailedTestClasses=true", getGradleStep("GRADLE_RERUNNER").gradleParams)
        assertEquals("clean myTask", getGradleStep("GRADLE_RUNNER").tasks)
        assertEquals("myTask", getGradleStep("GRADLE_RERUNNER").tasks)
    }

    private
    fun getGradleStep(stepName: String) = steps.items.find { it.name == stepName }!! as GradleBuildStep

    private
    fun expectedRunnerParam(daemon: String = "--daemon", extraParameters: String = "") =
        "-PmaxParallelForks=%maxParallelForks% -s $daemon --continue -I \"%teamcity.build.checkoutDir%/gradle/init-scripts/build-scan.init.gradle.kts\" -Dorg.gradle.internal.tasks.createops -Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url% $extraParameters -PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% \"-Dscan.tag.Check\" \"-Dscan.tag.\""
}
