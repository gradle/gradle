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

import gradlebuild.basics.kotlindsl.execAndGetStdout
import gradlebuild.identity.extension.ModuleIdentityExtension
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test


class DetermineBaselinesTest {
    private
    val project = ProjectBuilder.builder().build()

    @Before
    fun setUp() {
        project.serviceOf<GradlePropertiesController>().loadGradlePropertiesFrom(project.projectDir) // https://github.com/gradle/gradle/issues/13122
        project.file("version.txt").writeText("1.0")
        project.apply(plugin = "gradlebuild.module-identity")

        // mock project.execAndGetStdout
        mockkStatic("gradlebuild.basics.kotlindsl.Kotlin_dsl_upstream_candidatesKt")
    }

    @After
    fun cleanUp() {
        unmockkStatic("gradlebuild.basics.kotlindsl.Kotlin_dsl_upstream_candidatesKt")
    }

    @Test
    fun `determines defaults when configured as force-defaults`() {
        verifyBaselineDetermination(false, forceDefaultBaseline, defaultBaseline)
    }

    @Test
    fun `keeps flakiness-detection-commit as it is in coordinator build`() {
        verifyBaselineDetermination(true, flakinessDetectionCommitBaseline, flakinessDetectionCommitBaseline)
    }

    @Test
    fun `resolves to current commit in worker build`() {
        // given
        mockGitOperation(listOf("git", "rev-parse", "HEAD"), "current")
        mockGitOperation(listOf("git", "show", "current:version.txt"), "5.0")
        mockGitOperation(listOf("git", "rev-parse", "--short", "current"), "current")

        // then
        verifyBaselineDetermination(false, flakinessDetectionCommitBaseline, "5.0-commit-current")
    }

    @Test
    fun `determines fork point commit on feature branch and default configuration`() {
        // Windows git complains "long path" so we don't build commit distribution on Windows
        Assume.assumeFalse(OperatingSystem.current().isWindows)
        // given
        setCurrentBranch("my-branch")
        mockGitOperation(listOf("git", "fetch", "origin", "master", "release"), "")
        mockGitOperation(listOf("git", "merge-base", "origin/master", "HEAD"), "master-fork-point")
        mockGitOperation(listOf("git", "merge-base", "origin/release", "HEAD"), "release-fork-point")
        mockGitOperation(listOf("git", "show", "master-fork-point:version.txt"), "5.1")
        mockGitOperation(listOf("git", "rev-parse", "--short", "master-fork-point"), "master-fork-point")

        // then
        verifyBaselineDetermination(false, defaultBaseline, "5.1-commit-master-fork-point")
    }

    @Test
    fun `determines fork point commit on feature branch and empty configuration`() {
        // Windows git complains "long path" so we don't build commit distribution on Windows
        Assume.assumeFalse(OperatingSystem.current().isWindows)
        // given
        setCurrentBranch("my-branch")
        mockGitOperation(listOf("git", "fetch", "origin", "master", "release"), "")
        mockGitOperation(listOf("git", "merge-base", "origin/master", "HEAD"), "master-fork-point")
        mockGitOperation(listOf("git", "merge-base", "origin/release", "HEAD"), "release-fork-point")
        mockGitOperation(listOf("git", "show", "master-fork-point:version.txt"), "5.1")
        mockGitOperation(listOf("git", "rev-parse", "--short", "master-fork-point"), "master-fork-point")

        // then
        verifyBaselineDetermination(false, null, "5.1-commit-master-fork-point")
    }

    @Test
    fun `uses configured version on master branch`() {
        // given
        setCurrentBranch("master")

        // then
        verifyBaselineDetermination(false, defaultBaseline, defaultBaseline)
    }

    @Test
    fun `uses configured version when it is overwritten on feature branch`() {
        // given
        setCurrentBranch("my-branch")

        // then
        verifyBaselineDetermination(false, "any", "any")
    }

    private
    fun createDetermineBaselinesTask(isDistributed: Boolean) =
        project.tasks.create("determineBaselines", DetermineBaselines::class.java, isDistributed)

    private
    fun mockGitOperation(args: List<String>, expectedOutput: String) =
        every { project.execAndGetStdout(*(args.toTypedArray())) } returns expectedOutput

    private
    fun setCurrentBranch(branch: String) {
        project.the<ModuleIdentityExtension>().gradleBuildBranch.set(branch)
    }

    private
    fun verifyBaselineDetermination(isCoordinatorBuild: Boolean, configuredBaseline: String?, determinedBaseline: String) {
        // given
        val determineBaselinesTask = createDetermineBaselinesTask(isCoordinatorBuild)

        // when
        determineBaselinesTask.configuredBaselines.set(configuredBaseline)
        determineBaselinesTask.determineForkPointCommitBaseline()

        // then
        assert(determineBaselinesTask.determinedBaselines.get() == determinedBaseline)
    }
}
