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
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule
import vcsroots.gradlePromotionBranches

class PublishNightlySnapshot(branch: VersionedSettingsBranch) : PublishGradleDistributionFullBuild(
    promotedBranch = branch.branchName,
    prepTask = branch.prepNightlyTaskName(),
    promoteTask = branch.promoteNightlyTaskName(),
    triggerName = "ReadyforNightly",
    vcsRootId = gradlePromotionBranches
) {
    init {
        id("Promotion_Nightly")
        name = "Nightly Snapshot"
        description = "Promotes the latest successful changes on '${branch.branchName}' from Ready for Nightly as a new nightly snapshot"

        triggers {
            branch.nightlyPromotionTriggerHour?.let { triggerHour ->
                schedule {
                    if (branch.isMainBranch) {
                        schedulingPolicy = daily {
                            this.hour = triggerHour
                        }
                    } else {
                        schedulingPolicy = weekly {
                            this.dayOfWeek = ScheduleTrigger.DAY.Saturday
                            this.hour = triggerHour
                        }
                    }
                    triggerBuild = always()
                    withPendingChangesOnly = branch.isMainBranch
                    enabled = branch.enableVcsTriggers
                    // https://www.jetbrains.com/help/teamcity/2022.04/configuring-schedule-triggers.html#general-syntax-1
                    // We want it to be triggered only when there're pending changes in the specific vcs root, i.e. GradleMaster/GradleRelease
                    triggerRules = "+:root=${VersionedSettingsBranch.fromDslContext().vcsRootId()}:."
                    // The promotion itself will be triggered on gradle-promote's master branch
                    branchFilter = "+:master"
                }
            }
        }
    }
}
