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

import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import vcsroots.gradlePromotionBranches

object PublishBranchSnapshotFromQuickFeedback : PublishGradleDistributionFullBuild(
    promotedBranch = "%branch.to.promote%",
    triggerName = "QuickFeedback",
    prepTask = "prepSnapshot",
    promoteTask = "promoteSnapshot",
    extraParameters = "-PpromotedBranch=%branch.qualifier% ",
    vcsRootId = gradlePromotionBranches
) {
    init {
        id("Promotion_PublishBranchSnapshotFromQuickFeedback")
        name = "Publish Branch Snapshot (from Quick Feedback)"
        description = "Deploys a new distribution snapshot for the selected build/branch. Does not update master or the documentation."

        val triggerName = this.triggerName

        params {
            param("branch.qualifier", "%dep.${RelativeId("Check_Stage_${triggerName}_Trigger")}.teamcity.build.branch%")
            text(
                "branch.to.promote",
                "%branch.qualifier%",
                label = "Branch to promote",
                description = "Type in the branch of gradle/gradle you want to promote. Leave the default value when promoting an existing build.",
                display = ParameterDisplay.PROMPT,
                allowEmpty = false
            )
        }
    }
}
