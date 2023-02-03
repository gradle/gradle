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

package promotion

import common.VersionedSettingsBranch
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay

abstract class PublishRelease(
    prepTask: String,
    promoteTask: String,
    requiredConfirmationCode: String,
    promotedBranch: String,
    init: PublishRelease.() -> Unit = {}
) : PublishGradleDistributionFullBuild(
    promotedBranch = promotedBranch,
    prepTask = prepTask,
    promoteTask = promoteTask,
    triggerName = "ReadyforRelease",
    gitUserEmail = "%gitUserEmail%",
    gitUserName = "%gitUserName%",
    extraParameters = "-PconfirmationCode=%confirmationCode%"
) {
    init {
        params {
            text(
                "gitUserEmail",
                "",
                label = "Git user.email Configuration",
                description = "Enter the git 'user.email' configuration to commit change under",
                display = ParameterDisplay.PROMPT,
                allowEmpty = true
            )
            text(
                "confirmationCode",
                "",
                label = "Confirmation Code",
                description = "Enter the value '$requiredConfirmationCode' (no quotes) to confirm the promotion",
                display = ParameterDisplay.PROMPT,
                allowEmpty = false
            )
            text(
                "gitUserName",
                "",
                label = "Git user.name Configuration",
                description = "Enter the git 'user.name' configuration to commit change under",
                display = ParameterDisplay.PROMPT,
                allowEmpty = true
            )
        }

        cleanup {
            history(days = 180)
        }

        this.init()
    }
}

class PublishFinalRelease(branch: VersionedSettingsBranch) : PublishRelease(
    promotedBranch = branch.branchName,
    prepTask = "prepFinalRelease",
    promoteTask = branch.promoteFinalReleaseTaskName(),
    requiredConfirmationCode = "final",
    init = {
        id("Promotion_FinalRelease")
        name = "Release - Final"
        description = "Promotes the latest successful change on 'release' as a new release"
    }
)

class PublishReleaseCandidate(branch: VersionedSettingsBranch) : PublishRelease(
    promotedBranch = branch.branchName,
    prepTask = "prepRc",
    promoteTask = "promoteRc",
    requiredConfirmationCode = "rc",
    init = {
        id("Promotion_ReleaseCandidate")
        name = "Release - Release Candidate"
        description = "Promotes the latest successful change on 'release' as a new release candidate"
    }
)

class PublishMilestone(branch: VersionedSettingsBranch) : PublishRelease(
    promotedBranch = branch.branchName,
    prepTask = "prepMilestone",
    promoteTask = branch.promoteMilestoneTaskName(),
    requiredConfirmationCode = "milestone",
    init = {
        id("Promotion_Milestone")
        name = "Release - Milestone"
        description = "Promotes the latest successful change on '${branch.branchName}' as a new milestone"
    }
)
