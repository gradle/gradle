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
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import vcsroots.gradlePromotionBranches

class PublishBranchSnapshotFromQuickFeedback(branch: VersionedSettingsBranch) : PublishGradleDistributionFullBuild(
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

        // schedule nightly snapshots for provider API migration https://github.com/gradle/build-tool-roadmap/issues/28
        if (branch.isMaster) {
            branch.nightlyPromotionTriggerHour?.let { triggerHour ->
                triggers {
                    schedule {
                        schedulingPolicy = daily {
                            this.hour = triggerHour + 1 // trigger after the nightly build on the default branch
                        }
                        triggerBuild = always()
                        withPendingChangesOnly = true
                        enabled = branch.enableVcsTriggers
                        // https://www.jetbrains.com/help/teamcity/2022.04/configuring-schedule-triggers.html#general-syntax-1
                        // We want it to be triggered only when there are pending changes in the specific vcs root, i.e. GradleMaster/GradleRelease
                        triggerRules = "+:root=${VersionedSettingsBranch.fromDslContext().vcsRootId()}:."
                        // The promotion itself will be triggered on gradle-promote's master branch
                        branchFilter = "+:master"
                        buildParams {
                            param("branch.qualifier", "provider-api-migration/public-api-changes")
                        }
                    }
                }
            }
        }
    }
}
