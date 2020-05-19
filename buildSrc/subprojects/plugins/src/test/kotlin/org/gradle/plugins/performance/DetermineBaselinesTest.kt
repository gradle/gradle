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

package org.gradle.plugins.performance


import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*
import org.gradle.process.ExecResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import GitInformationExtension
import gitInfo


class DetermineBaselinesTest {
    private
    val project = ProjectBuilder.builder().build()

    private
    val provider = mockk<Provider<String>>()


    @Before
    fun setUp() {
        // mock project.execAndGetStdout
        mockkStatic("org.gradle.kotlin.dsl.Kotlin_dsl_upstream_candidatesKt")

        // mock project.gitInfo
        mockkStatic("Versioning_extensionsKt")

        mockkObject(project)
    }

    @After
    fun cleanUp() {
        unmockkStatic("org.gradle.kotlin.dsl.Kotlin_dsl_upstream_candidatesKt")
        unmockkStatic("Versioning_extensionsKt")
        unmockkObject(project)
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
        // given
        mockCurrentBranch("my-branch")
        mockGitOperation(listOf("git", "fetch", "origin", "master", "release"), "")
        mockGitOperation(listOf("git", "merge-base", "origin/master", "HEAD"), "master-fork-point")
        mockGitOperation(listOf("git", "merge-base", "origin/release", "HEAD"), "release-fork-point")
        mockProjectExec(1)
        mockGitOperation(listOf("git", "show", "master-fork-point:version.txt"), "5.1")
        mockGitOperation(listOf("git", "rev-parse", "--short", "master-fork-point"), "master-fork-point")

        // then
        verifyBaselineDetermination(false, defaultBaseline, "5.1-commit-master-fork-point")
    }

    @Test
    fun `determines fork point commit on feature branch and empty configuration`() {
        // given
        mockCurrentBranch("my-branch")
        mockGitOperation(listOf("git", "fetch", "origin", "master", "release"), "")
        mockGitOperation(listOf("git", "merge-base", "origin/master", "HEAD"), "master-fork-point")
        mockGitOperation(listOf("git", "merge-base", "origin/release", "HEAD"), "release-fork-point")
        mockProjectExec(1)
        mockGitOperation(listOf("git", "show", "master-fork-point:version.txt"), "5.1")
        mockGitOperation(listOf("git", "rev-parse", "--short", "master-fork-point"), "master-fork-point")

        // then
        verifyBaselineDetermination(false, null, "5.1-commit-master-fork-point")
    }

    @Test
    fun `uses configured version on master branch`() {
        // given
        mockCurrentBranch("master")

        // then
        verifyBaselineDetermination(false, defaultBaseline, defaultBaseline)
    }

    @Test
    fun `uses configured version when it is overwritten on feature branch`() {
        // given
        mockCurrentBranch("my-branch")

        // then
        verifyBaselineDetermination(false, "any", "any")
    }

    private
    fun createDetermineBaselinesTask(isDistributed: Boolean) =
        project.tasks.create("determineBaselines", DetermineBaselines::class.java, isDistributed)

    private
    fun mockProjectExec(expectedReturnValue: Int) {
        val mockResult = mockk<ExecResult>()
        every { mockResult.exitValue } returns expectedReturnValue
        every { project.exec(any<Action<Any>>()) } returns mockResult
    }

    private
    fun mockGitOperation(args: List<String>, expectedOutput: String) =
        every { project.execAndGetStdout(*(args.toTypedArray())) } returns expectedOutput

    private
    fun mockCurrentBranch(branch: String) {
        every { project.gitInfo } returns GitInformationExtension(provider, provider)
        every { provider.get() } returns branch
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
