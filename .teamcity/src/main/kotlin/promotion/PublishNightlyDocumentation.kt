/*
 * Copyright 2024 the original author or authors.
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
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import model.StageName
import vcsroots.gradlePromotionBranches

const val NIGHTLY_DOCUMENTATION_PROMOTION_ID = "Promotion_NightlyDocumentation"
class PublishNightlyDocumentation(branch: VersionedSettingsBranch) : PublishGradleDistributionFullBuild(
    promotedBranch = branch.branchName,
    promoteTask = "publishBranchDocs",
    triggerName = StageName.PULL_REQUEST_FEEDBACK.uuid,
    vcsRootId = gradlePromotionBranches
) {
    init {
        id(NIGHTLY_DOCUMENTATION_PROMOTION_ID)
        name = "Nightly Documentation"
        description = "Promotes the latest successful documentation changes on '${branch.branchName}' from Ready for Nightly as a new nightly documentation snapshot"
    }
}
