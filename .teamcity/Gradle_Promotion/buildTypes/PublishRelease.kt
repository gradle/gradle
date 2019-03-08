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

package Gradle_Promotion.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay

abstract class PublishRelease(task: String, requiredConfirmationCode: String, branch: String = "release", init: PublishRelease.() -> Unit = {}) : PublishGradleDistribution(
    branch = branch,
    task = task,
    triggerName = "ReadyforRelease",
    gitUserEmail = "%gitUserEmail%",
    gitUserName = "%gitUserName%",
    extraParameters = "-PconfirmationCode=%confirmationCode%"
) {
    init {
        params {
            text("gitUserEmail", "", label = "Git user.email Configuration", description = "Enter the git 'user.email' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
            text("confirmationCode", "", label = "Confirmation Code", description = "Enter the value '$requiredConfirmationCode' (no quotes) to confirm the promotion", display = ParameterDisplay.PROMPT, allowEmpty = false)
            text("gitUserName", "", label = "Git user.name Configuration", description = "Enter the git 'user.name' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
        }
        this.init()
    }
}

object PublishFinalRelease : PublishRelease(task = "promoteFinalRelease", requiredConfirmationCode = "final", init = {
    uuid = "44e9390f-e46c-457e-aa18-31b020aef4de"
    id("Gradle_Promotion_FinalRelease")
    name = "Release - Final"
    description = "Promotes the latest successful change on 'release' as a new release"
})

object PublishReleaseCandidate : PublishRelease(task = "promoteRc", requiredConfirmationCode = "rc", init = {
    uuid = "5ed504bb-5ec3-46dc-a28a-e42a63ebbb31"
    id("Gradle_Promotion_ReleaseCandidate")
    name = "Release - Release Candidate"
    description = "Promotes the latest successful change on 'release' as a new release candidate"
})

object PublishMilestone : PublishRelease(task = "promoteMilestone", requiredConfirmationCode = "milestone", init = {
    uuid = "2ffb238a-08af-4f95-b863-9830d2bc3872"
    id("Gradle_Promotion_Milestone")
    name = "Release - Milestone"
    description = "Promotes the latest successful change on 'release' as a new milestone"
})
