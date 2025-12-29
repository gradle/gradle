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
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger.SchedulingPolicy
import jetbrains.buildServer.configs.kotlin.triggers.schedule

const val NIGHTLY_SNAPSHOT_BUILD_ID = "Promotion_Nightly"

/**
 * 0~23.
 * To avoid nightly promotion jobs running at the same time,
 * we run each branch on different hours.
 * master - 0:00
 * release - 1:00
 * release6x - 2:00
 * release7x - 3:00
 * ...
 * releaseNx - (N-4):00
 */
fun VersionedSettingsBranch.determineNightlyPromotionTriggerHour(): Int? {
    val oldReleasePattern = "release(\\d+)x".toRegex()
    return when (branchName) {
        "master" -> 0
        "release" -> 1
        else -> {
            val matchResult = oldReleasePattern.find(branchName)
            if (matchResult == null) {
                null
            } else {
                (matchResult.groupValues[1].toInt() - 4).apply {
                    require(this in 2..23)
                }
            }
        }
    }
}

fun ScheduleTrigger.scheduledTrigger(
    branch: VersionedSettingsBranch,
    policy: SchedulingPolicy,
    pendingChangesOnly: Boolean,
) {
    schedulingPolicy = policy
    triggerBuild = always()
    withPendingChangesOnly = pendingChangesOnly
    enabled = true
    // https://www.jetbrains.com/help/teamcity/2022.04/configuring-schedule-triggers.html#general-syntax-1
    // We want it to be triggered only when there're pending changes in the specific vcs root, i.e. GradleMaster/GradleRelease
    triggerRules = "+:root=${branch.vcsRootId()}:."
    branchFilter = "+:<default>"
}

class PublishNightlySnapshot(
    branch: VersionedSettingsBranch,
) : PublishGradleDistributionFullBuild(
        promotedBranch = branch.branchName,
        prepTask = branch.prepNightlyTaskName(),
        promoteTask = branch.promoteNightlyTaskName(),
        triggerName = "ReadyforNightly",
    ) {
    init {
        id(NIGHTLY_SNAPSHOT_BUILD_ID)
        name = "Nightly Snapshot"
        description =
            "Promotes the latest successful changes on '${branch.branchName}' from Ready for Nightly as a new nightly snapshot"

        triggers {
            val triggerHour = branch.determineNightlyPromotionTriggerHour() ?: return@triggers
            // for master/release branch, we trigger them on midnight (with pending change only, i.e. if there is no change, don't rigger it)
            // for old release branches, we trigger them on midnight if there is change, or unconditionally on weekends
            when {
                branch.isMaster || branch.isRelease ->
                    schedule {
                        scheduledTrigger(
                            branch,
                            policy = daily { hour = triggerHour },
                            pendingChangesOnly = true,
                        )
                    }

                else -> {
                    schedule {
                        scheduledTrigger(
                            branch,
                            policy = daily { hour = triggerHour },
                            pendingChangesOnly = true,
                        )
                    }

                    schedule {
                        scheduledTrigger(
                            branch,
                            policy =
                                weekly {
                                    dayOfWeek = ScheduleTrigger.DAY.Saturday
                                    hour = triggerHour
                                },
                            pendingChangesOnly = false,
                        )
                    }
                }
            }
        }
    }
}
