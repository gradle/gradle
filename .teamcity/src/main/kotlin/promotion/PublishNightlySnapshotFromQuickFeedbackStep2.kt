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

class PublishNightlySnapshotFromQuickFeedbackStep2(branch: VersionedSettingsBranch) : BasePublishGradleDistribution(
    promotedBranch = branch.branchName,
    triggerName = "QuickFeedback",
    vcsRootId = gradlePromotionBranches,
    cleanCheckout = false
) {
    init {
        id("Promotion_SnapshotFromQuickFeedbackStep2")
        name = "Nightly Snapshot (from QuickFeedback) - Step 2"
        description = "Promotes a previously built distribution on this agent on '${branch.branchName}' from Quick Feedback as a new nightly snapshot"

        steps {
            buildStep(
                this@PublishNightlySnapshotFromQuickFeedbackStep2.extraParameters,
                this@PublishNightlySnapshotFromQuickFeedbackStep2.gitUserName,
                this@PublishNightlySnapshotFromQuickFeedbackStep2.gitUserEmail,
                this@PublishNightlySnapshotFromQuickFeedbackStep2.triggerName,
                branch.prepNightlyTaskName(),
                branch.promoteNightlyTaskName()
            )
        }
    }
}
