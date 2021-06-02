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
import configurations.branchFilter
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule

class PublishNightlySnapshot(branch: VersionedSettingsBranch) : PublishGradleDistribution(
    promotedBranch = branch.branchName,
    task = branch.promoteNightlyTaskName(),
    triggerName = "ReadyforNightly"
) {
    init {
        id("Promotion_Nightly")
        name = "Nightly Snapshot"
        description = "Promotes the latest successful changes on '${branch.branchName}' from Ready for Nightly as a new nightly snapshot"

        triggers {
            schedule {
                branch.triggeredHour()?.apply {
                    schedulingPolicy = daily {
                        this.hour = this@apply
                    }
                }
                triggerBuild = always()
                withPendingChangesOnly = true
                enabled = branch.enableTriggers
                branchFilter = branch.branchFilter()
            }
        }
    }
}

// Avoid two jobs running at the same time and causing troubles
private fun VersionedSettingsBranch.triggeredHour() = when {
    isMaster -> 0
    isRelease -> 1
    else -> null
}
