/*
 * Copyright 2022 the original author or authors.
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

import common.VersionedSettingsBranch
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import promotion.PromotionProject

class PromotionProjectTests {
    @Test
    fun `promotion project has expected build types for master branch`() {
        val model = setupModelFor("master")

        assertEquals("Promotion", model.name)
        assertEquals(9, model.buildTypes.size)
        assertEquals(
            listOf("SanityCheck", "Nightly Snapshot", "Nightly Snapshot (from QuickFeedback)", "Nightly Snapshot (from QuickFeedback) - Step 1", "Nightly Snapshot (from QuickFeedback) - Step 2", "Publish Branch Snapshot (from Quick Feedback)", "Release - Milestone", "Start Release Cycle", "Start Release Cycle Test"),
            model.buildTypes.map { it.name }
        )
    }

    @Test
    fun `promotion project has expected build types for other branches`() {
        val model = setupModelFor("release")

        assertEquals("Promotion", model.name)
        assertEquals(9, model.buildTypes.size)
        assertEquals(
            listOf("SanityCheck", "Nightly Snapshot", "Nightly Snapshot (from QuickFeedback)", "Nightly Snapshot (from QuickFeedback) - Step 1", "Nightly Snapshot (from QuickFeedback) - Step 2", "Publish Branch Snapshot (from Quick Feedback)", "Release - Milestone", "Release - Release Candidate", "Release - Final"),
            model.buildTypes.map { it.name }
        )
    }

    @Test
    fun `promotion sanity check runs 'gradle tasks'`() {
        val model = setupModelFor("release")

        val sanityCheck = model.findBuildTypeByName("SanityCheck")
        val steps = sanityCheck.steps.items
        val gradleBuildStep = gradleStep(steps, 0)
        gradleBuildStep.assertTasks("tasks")
    }

    @Test
    fun `nightly promotion build types runs one gradle invocation`() {
        val model = setupModelFor("release")

        val sanityCheck = model.findBuildTypeByName("Nightly Snapshot")
        val steps = sanityCheck.steps.items
        val upload = gradleStep(steps, 0)
        upload.assertTasks("uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforNightly_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" """, upload.gradleParams)

        val promote = gradleStep(steps, 1)
        promote.assertTasks("promoteReleaseNightly")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforNightly_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" """, promote.gradleParams)
    }

    // TODO: Add tests for other promotion build types
    // TODO: Add proper task for first upload step

    private fun setupModelFor(branchName: String): PromotionProject {
        // Set the project id here, so we can use methods on the DslContext
        DslContext.projectId = AbsoluteId("Gradle_$branchName")
        DslContext.addParameters("Branch" to branchName)
        val model = PromotionProject(VersionedSettingsBranch(branchName, true))
        return model
    }

    private fun gradleStep(steps: List<BuildStep>, index: Int): GradleBuildStep {
        assertTrue(steps.size > index)
        return steps[index] as GradleBuildStep
    }

    private fun GradleBuildStep.assertTasks(expectedTasks: String) = assertEquals(expectedTasks, this.tasks)
    private fun PromotionProject.findBuildTypeByName(name: String) = this.buildTypes.find { it.name == name }!!
}
