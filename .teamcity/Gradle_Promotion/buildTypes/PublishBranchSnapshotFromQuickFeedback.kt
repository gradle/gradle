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

object PublishBranchSnapshotFromQuickFeedback : PublishGradleDistribution(
    branch = "%branch.to.promote%",
    triggerName = "QuickFeedback",
    task = "promoteSnapshot",
    extraParameters = "-PpromotedBranch=%branch.qualifier% ",
    vcsRoot = Gradle_Promotion.vcsRoots.Gradle_Promotion_GradlePromotionBranches
) {
    init {
        uuid = "b7ecebd3-3812-4532-aa77-5679f9e9d6b3"
        id("Gradle_Promotion_PublishBranchSnapshotFromQuickFeedback")
        name = "Publish Branch Snapshot (from Quick Feedback)"
        description = "Deploys a new distribution snapshot for the selected build/branch. Does not update master or the documentation."

        val triggerName = this.triggerName

        params {
            param("branch.qualifier", "%dep.Gradle_Check_Stage_${triggerName}_Trigger.teamcity.build.branch%")
            text("branch.to.promote", "%branch.qualifier%", label = "Branch to promote", description = "Type in the branch of gradle/gradle you want to promote. Leave the default value when promoting an existing build.", display = ParameterDisplay.PROMPT, allowEmpty = false)
        }
    }
}
