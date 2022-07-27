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
        assertEquals(10, model.buildTypes.size)
        assertEquals(
            listOf("SanityCheck", "Nightly Snapshot", "Nightly Snapshot (from QuickFeedback)", "Nightly Snapshot (from QuickFeedback) - Check Ready", "Nightly Snapshot (from QuickFeedback) - Upload", "Nightly Snapshot (from QuickFeedback) - Promote", "Publish Branch Snapshot (from Quick Feedback)", "Release - Milestone", "Start Release Cycle", "Start Release Cycle Test"),
            model.buildTypes.map { it.name }
        )
    }

    @Test
    fun `promotion project has expected build types for other branches`() {
        val model = setupModelFor("release")

        assertEquals("Promotion", model.name)
        assertEquals(10, model.buildTypes.size)
        assertEquals(
            listOf("SanityCheck", "Nightly Snapshot", "Nightly Snapshot (from QuickFeedback)", "Nightly Snapshot (from QuickFeedback) - Check Ready", "Nightly Snapshot (from QuickFeedback) - Upload", "Nightly Snapshot (from QuickFeedback) - Promote", "Publish Branch Snapshot (from Quick Feedback)", "Release - Milestone", "Release - Release Candidate", "Release - Final"),
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
    fun `nightly promotion build type runs three gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Nightly Snapshot")

        val steps = nightlySnapshot.steps.items
        assertEquals(3, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepReleaseNightly checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforNightly_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepReleaseNightly uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforNightly_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, upload.gradleParams)

        val promote = gradleStep(steps, 2)
        promote.assertTasks("prepReleaseNightly promoteReleaseNightly")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforNightly_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, promote.gradleParams)
    }

    @Test
    fun `start release cycle promotion build type runs one gradle invocation`() {
        val model = setupModelFor("master")
        val startReleaseCycle = model.findBuildTypeByName("Start Release Cycle")

        val steps = startReleaseCycle.steps.items
        assertEquals(1, steps.size)

        val step = gradleStep(steps, 0)
        step.assertTasks("clean promoteStartReleaseCycle")
        assertEquals("""-PcommitId=%dep.Gradle_master_Check_Stage_ReadyforNightly_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" """, step.gradleParams)
    }

    @Test
    fun `start release cycle test promotion build type runs one gradle invocation`() {
        val model = setupModelFor("master")
        val startReleaseCycle = model.findBuildTypeByName("Start Release Cycle Test")

        val steps = startReleaseCycle.steps.items
        assertEquals(1, steps.size)

        val step = gradleStep(steps, 0)
        step.assertTasks("clean promoteStartReleaseCycle")
        assertTrue(step.gradleParams!!.startsWith("""-PconfirmationCode=startCycle -PtestRun=1"""))
    }

    @Test
    fun `nightly promotion from quick feedback build type runs three gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Nightly Snapshot (from QuickFeedback)")

        val steps = nightlySnapshot.steps.items
        assertEquals(3, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepReleaseNightly checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepReleaseNightly uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, upload.gradleParams)

        val promote = gradleStep(steps, 2)
        promote.assertTasks("prepReleaseNightly promoteReleaseNightly")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, promote.gradleParams)
    }

    @Test
    fun `publish branch snapshot build type runs three gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Publish Branch Snapshot (from Quick Feedback)")

        val steps = nightlySnapshot.steps.items
        assertEquals(3, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepSnapshot checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_master_Check_Stage_QuickFeedback_Trigger.build.vcs.number% -PpromotedBranch=%branch.qualifier%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepSnapshot uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_master_Check_Stage_QuickFeedback_Trigger.build.vcs.number% -PpromotedBranch=%branch.qualifier%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, upload.gradleParams)

        val promote = gradleStep(steps, 2)
        promote.assertTasks("prepSnapshot promoteSnapshot")
        assertEquals("""-PcommitId=%dep.Gradle_master_Check_Stage_QuickFeedback_Trigger.build.vcs.number% -PpromotedBranch=%branch.qualifier%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, promote.gradleParams)
    }

    @Test
    fun `nightly promotion from quick feedback step 1 build type runs two gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Nightly Snapshot (from QuickFeedback) - Upload")

        val steps = nightlySnapshot.steps.items
        assertEquals(2, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepReleaseNightly checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepReleaseNightly uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, upload.gradleParams)
    }

    @Test
    fun `nightly promotion from quick feedback step 2 build type runs two gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Nightly Snapshot (from QuickFeedback) - Promote")

        val steps = nightlySnapshot.steps.items
        assertEquals(2, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepReleaseNightly checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepReleaseNightly promoteReleaseNightly")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_QuickFeedback_Trigger.build.vcs.number%  "-PgitUserName=bot-teamcity" "-PgitUserEmail=bot-teamcity@gradle.com" %additional.gradle.parameters% """, upload.gradleParams)
    }

    @Test
    fun `publish final release build type runs three gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Release - Final")

        val steps = nightlySnapshot.steps.items
        assertEquals(3, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepFinalRelease checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepFinalRelease uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, upload.gradleParams)

        val promote = gradleStep(steps, 2)
        promote.assertTasks("prepFinalRelease promoteFinalRelease")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, promote.gradleParams)
    }

    @Test
    fun `publish rc build type runs three gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Release - Release Candidate")

        val steps = nightlySnapshot.steps.items
        assertEquals(3, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepRc checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepRc uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, upload.gradleParams)

        val promote = gradleStep(steps, 2)
        promote.assertTasks("prepRc promoteRc")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, upload.gradleParams)
    }

    @Test
    fun `publish milestone build type runs three gradle invocations`() {
        val model = setupModelFor("release")
        val nightlySnapshot = model.findBuildTypeByName("Release - Milestone")

        val steps = nightlySnapshot.steps.items
        assertEquals(3, steps.size)

        val checkReady = gradleStep(steps, 0)
        checkReady.assertTasks("prepMilestone checkNeedToPromote")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, checkReady.gradleParams)

        val upload = gradleStep(steps, 1)
        upload.assertTasks("prepMilestone uploadAll")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, upload.gradleParams)

        val promote = gradleStep(steps, 2)
        promote.assertTasks("prepMilestone promoteMilestone")
        assertEquals("""-PcommitId=%dep.Gradle_release_Check_Stage_ReadyforRelease_Trigger.build.vcs.number% -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" %additional.gradle.parameters% """, upload.gradleParams)
    }

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
