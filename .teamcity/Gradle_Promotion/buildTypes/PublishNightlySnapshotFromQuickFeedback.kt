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

package Gradle_Promotion.buildTypes

class PublishNightlySnapshotFromQuickFeedback(uuid: String, branch: String) : PublishGradleDistribution(
    branch = branch,
    task = branch.promoteNightlyTaskName(),
    triggerName = "QuickFeedback"
) {
    init {
        this.uuid = uuid
        id("Gradle_Promotion_${branch.capitalize()}SnapshotFromQuickFeedback")
        name = "${branch.capitalize()} - Nightly Snapshot (from QuickFeedback)"
        description = "Promotes the latest successful changes on '$branch' from Quick Feedback as a new nightly snapshot"
    }
}
