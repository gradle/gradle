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
import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.triggers.finishBuildTrigger

object MergeReleaseIntoMaster : BasePromotionBuildType() {
    init {
        id("Promotion_MergeReleaseIntoMaster")
        name = "Merge Release into Master"
        description = "Merge Release into Master"

        steps {
            gradleWrapper {
                name = "Merge Release into Master"
                tasks =
                    listOf(
                        "updateReleaseVersionsOnMaster",
                        "gitMergeReleaseToMaster",
                        "createPreTestCommitPullRequestMergeReleaseIntoMaster",
                        "-PtriggeredBy=\"%teamcity.build.triggeredBy%\"",
                    ).joinToString(" ")
                useGradleWrapper = true
            }
        }

        triggers {
            finishBuildTrigger {
                buildType = "Gradle_${VersionedSettingsBranch.fromDslContext().branchName.uppercase()}_${NIGHTLY_SNAPSHOT_BUILD_ID}"
                successfulOnly = true
                branchFilter = "+:*"
            }
        }

        dependencies {
            dependency(
                AbsoluteId("Gradle_${VersionedSettingsBranch.fromDslContext().branchName.uppercase()}_${NIGHTLY_SNAPSHOT_BUILD_ID}"),
            ) {
                artifacts {
                    buildRule = lastSuccessful()
                    artifactRules = "version-info.properties => ./"
                }
            }
        }
    }
}
