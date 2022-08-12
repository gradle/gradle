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

class PublishNightlySnapshotFromQuickFeedbackStepUpload(branch: VersionedSettingsBranch) : BasePublishGradleDistribution(
    promotedBranch = branch.branchName,
    prepTask = branch.prepNightlyTaskName(),
    triggerName = "QuickFeedback",
    vcsRootId = gradlePromotionBranches
) {
    init {
        id("Promotion_SnapshotFromQuickFeedbackStepUpload")
        name = "Nightly Snapshot (from QuickFeedback) - Upload"
        description = "Builds and uploads the latest successful changes on '${branch.branchName}' from Quick Feedback as a new distribution"

        steps {
            buildStep(
                this@PublishNightlySnapshotFromQuickFeedbackStepUpload.extraParameters,
                this@PublishNightlySnapshotFromQuickFeedbackStepUpload.gitUserName,
                this@PublishNightlySnapshotFromQuickFeedbackStepUpload.gitUserEmail,
                this@PublishNightlySnapshotFromQuickFeedbackStepUpload.triggerName,
                branch.prepNightlyTaskName(),
                "uploadAll"
            )
        }
    }
}
