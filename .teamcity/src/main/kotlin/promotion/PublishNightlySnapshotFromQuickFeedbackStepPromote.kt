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
import vcsroots.gradlePromotionBranches

class PublishNightlySnapshotFromQuickFeedbackStepPromote(branch: VersionedSettingsBranch) : BasePublishGradleDistribution(
    promotedBranch = branch.branchName,
    prepTask = branch.prepNightlyTaskName(),
    triggerName = "QuickFeedback",
    vcsRootId = gradlePromotionBranches,
    cleanCheckout = false
) {
    init {
        id("Promotion_SnapshotFromQuickFeedbackStepPromote")
        name = "Nightly Snapshot (from QuickFeedback) - Promote"
        description = "Promotes a previously built distribution on this agent on '${branch.branchName}' from Quick Feedback as a new nightly snapshot. This build checks out gradle-promote, so don't be misled by the 'master' branch."

        steps {
            buildStep(
                this@PublishNightlySnapshotFromQuickFeedbackStepPromote.extraParameters,
                this@PublishNightlySnapshotFromQuickFeedbackStepPromote.gitUserName,
                this@PublishNightlySnapshotFromQuickFeedbackStepPromote.gitUserEmail,
                this@PublishNightlySnapshotFromQuickFeedbackStepPromote.triggerName,
                branch.prepNightlyTaskName(),
                branch.promoteNightlyTaskName()
            )
        }
    }
}
