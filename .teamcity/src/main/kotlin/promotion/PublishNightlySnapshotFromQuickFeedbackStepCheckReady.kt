/*
 * Copyright 2022 the original author or authors.
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

class PublishNightlySnapshotFromQuickFeedbackStepCheckReady(
    branch: VersionedSettingsBranch,
) : BasePublishGradleDistribution(
        promotedBranch = branch.branchName,
        prepTask = branch.prepNightlyTaskName(),
        triggerName = "QuickFeedback",
        vcsRootId = gradlePromotionBranches,
        cleanCheckout = false,
    ) {
    init {
        id("Promotion_SnapshotFromQuickFeedbackStepCheckReady")
        name = "Nightly Snapshot (from QuickFeedback) - Check Ready"
        description = "Checks that a nightly snapshot can be published from QuickFeedback"
    }
}
