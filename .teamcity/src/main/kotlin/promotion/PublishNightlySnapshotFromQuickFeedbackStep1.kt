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

class PublishNightlySnapshotFromQuickFeedbackStep1(branch: VersionedSettingsBranch) : BasePublishGradleDistribution(
    promotedBranch = branch.branchName,
    task = branch.promoteNightlyTaskName(),
    triggerName = "QuickFeedback",
    vcsRootId = gradlePromotionBranches
) {
    init {
        id("Promotion_SnapshotFromQuickFeedbackStep1")
        name = "Nightly Snapshot (from QuickFeedback) - Step 1"
        description = "Builds and uploads the latest successful changes on '${branch.branchName}' from Quick Feedback as a new distribution"

        steps {
            buildStep1(
                this@PublishNightlySnapshotFromQuickFeedbackStep1.extraParameters,
                this@PublishNightlySnapshotFromQuickFeedbackStep1.gitUserName,
                this@PublishNightlySnapshotFromQuickFeedbackStep1.gitUserEmail,
                this@PublishNightlySnapshotFromQuickFeedbackStep1.triggerName
            )
        }
    }
}
