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

package gradlebuild.performance.tasks

import gradlebuild.basics.BuildEnvironmentExtension
import gradlebuild.basics.kotlindsl.execAndGetStdout
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.gradle.internal.os.OperatingSystem
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions


class DetermineBaselinesTest {
    private
    val project = ProjectBuilder.builder().build()

    private
    val buildEnvironmentExtension = project.extensions.create("buildEnvironment", BuildEnvironmentExtension::class.java)


    private
    val defaultPerformanceBaselines = "7.5-commit-123456"

    @Before
    fun setUp() {
        project.file("version.txt").writeText("1.0")

        // mock project.execAndGetStdout
        mockkStatic("gradlebuild.basics.kotlindsl.Kotlin_dsl_upstream_candidatesKt")
        mockGitOperation(listOf("git", "remote", "-v"), "origin https://github.com/gradle/gradle.git (fetch)")
    }

    @After
    fun cleanUp() {
        unmockkStatic("gradlebuild.basics.kotlindsl.Kotlin_dsl_upstream_candidatesKt")
    }

    @Test
    fun `keeps flakiness-detection-commit as it is in coordinator build`() {
        verifyBaselineDetermination("any", true, flakinessDetectionCommitBaseline, flakinessDetectionCommitBaseline)
    }

    @Test
    fun `resolves to current commit in worker build`() {
        // given
        mockGitOperation(listOf("git", "rev-parse", "HEAD"), "current")
        mockGitOperation(listOf("git", "show", "current:version.txt"), "5.0")
        mockGitOperation(listOf("git", "rev-parse", "--short", "current"), "current")

        // then
        verifyBaselineDetermination("any", false, flakinessDetectionCommitBaseline, "5.0-commit-current")
    }

    @Test
    fun `determines fork point commit on feature branch and default configuration`() {
        // given
        mockGitOperation(listOf("git", "fetch", "origin", "master", "release"), "")
        mockGitOperation(listOf("git", "merge-base", "origin/master", "HEAD"), "master-fork-point")
        mockGitOperation(listOf("git", "merge-base", "origin/release", "HEAD"), "release-fork-point")
        mockGitOperation(listOf("git", "show", "master-fork-point:version.txt"), "5.1")
        mockGitOperation(listOf("git", "rev-parse", "--short", "master-fork-point"), "master-fork-point")

        // then
        verifyBaselineDetermination("my-branch", false, null, "5.1-commit-master-fork-point")
    }

    @Test
    fun `not determines fork point commit in security advisory fork`() {
        // given
        mockGitOperation(listOf("git", "remote", "-v"), "origin https://github.com/gradle/gradle-ghsa-84mw-qh6q-v842.git (fetch)")

        // then
        verifyBaselineDetermination("my-branch", false, null, defaultPerformanceBaselines)
    }

    @Test
    fun `determines fork point commit on feature branch and empty configuration`() {
        // Windows git complains "long path" so we don't build commit distribution on Windows
        Assume.assumeFalse(OperatingSystem.current().isWindows)
        // given
        mockGitOperation(listOf("git", "fetch", "origin", "master", "release"), "")
        mockGitOperation(listOf("git", "merge-base", "origin/master", "HEAD"), "master-fork-point")
        mockGitOperation(listOf("git", "merge-base", "origin/release", "HEAD"), "release-fork-point")
        mockGitOperation(listOf("git", "show", "master-fork-point:version.txt"), "5.1")
        mockGitOperation(listOf("git", "rev-parse", "--short", "master-fork-point"), "master-fork-point")

        // then
        verifyBaselineDetermination("my-branch", false, null, "5.1-commit-master-fork-point")
    }

    @Test
    fun `uses configured version on master branch`() {
        verifyBaselineDetermination("master", false, defaultPerformanceBaselines, defaultPerformanceBaselines)
    }

    @Test
    fun `uses configured version when it is overwritten on feature branch`() {
        verifyBaselineDetermination("my-branch", false, "any", "any")
    }

    private
    fun createDetermineBaselinesTask(isDistributed: Boolean) =
        project.tasks.create("determineBaselines", DetermineBaselines::class.java, isDistributed)

    private
    fun mockGitOperation(args: List<String>, expectedOutput: String) =
        every { project.execAndGetStdout(*(args.toTypedArray())) } returns expectedOutput

    private
    fun setCurrentBranch(branch: String) {
        buildEnvironmentExtension.gitBranch = branch
    }

    private
    fun verifyBaselineDetermination(currentBranch: String, isCoordinatorBuild: Boolean, configuredBaseline: String?, determinedBaseline: String) {
        // given
        val determineBaselinesTask = createDetermineBaselinesTask(isCoordinatorBuild)

        // when
        determineBaselinesTask.logicalBranch = currentBranch
        determineBaselinesTask.configuredBaselines = configuredBaseline
        determineBaselinesTask.defaultBaselines = defaultPerformanceBaselines
        determineBaselinesTask.determineForkPointCommitBaseline()

        // then
        Assertions.assertEquals(determinedBaseline, determineBaselinesTask.determinedBaselines.get())
    }
}
