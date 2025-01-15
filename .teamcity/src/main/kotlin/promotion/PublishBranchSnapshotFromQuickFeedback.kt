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

import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.RelativeId
import vcsroots.gradlePromotionBranches

object PublishBranchSnapshotFromQuickFeedback : PublishGradleDistributionFullBuild(
    promotedBranch = "%branch.qualifier%",
    triggerName = "QuickFeedback",
    prepTask = "prepSnapshot",
    promoteTask = "promoteSnapshot",
    extraParameters = "-PpromotedBranch=%branch.qualifier%",
    vcsRootId = gradlePromotionBranches,
) {
    init {
        id("Promotion_PublishBranchSnapshotFromQuickFeedback")
        name = "Publish Branch Snapshot (from Quick Feedback)"
        description = "Deploys a new distribution snapshot for the selected build/branch. Does not update master or the documentation."

        val triggerName = this.triggerName

        params {
            text(
                "branch.qualifier",
                "%dep.${RelativeId("Check_Stage_${triggerName}_Trigger")}.teamcity.build.branch%",
                label = "Branch qualifier for the published distribution version",
                description = "The published distribution version looks like '8.13-branch-%branch.qualifier%-20241217145847+0000'.",
                display = ParameterDisplay.PROMPT,
                allowEmpty = false
            )
        }
    }
}
