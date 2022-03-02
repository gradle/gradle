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
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import vcsroots.gradlePromotionMaster

abstract class PublishGradleDistribution(
    // The branch to be promoted
    promotedBranch: String,
    task: String,
    val triggerName: String,
    gitUserName: String = "bot-teamcity",
    gitUserEmail: String = "bot-teamcity@gradle.com",
    extraParameters: String = "",
    vcsRootId: String = gradlePromotionMaster
) : BasePromotionBuildType(vcsRootId) {

    init {
        artifactRules = """
        **/build/git-checkout/subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
        **/build/distributions/*.zip => promote-build-distributions
        **/build/website-checkout/data/releases.xml
        **/build/git-checkout/build/reports/integTest/** => distribution-tests
        **/smoke-tests/build/reports/tests/** => post-smoke-tests
        """.trimIndent()

        steps {
            gradleWrapper {
                name = "Promote"
                tasks = task
                gradleParams = """-PcommitId=%dep.${RelativeId("Check_Stage_${this@PublishGradleDistribution.triggerName}_Trigger")}.build.vcs.number% $extraParameters "-PgitUserName=$gitUserName" "-PgitUserEmail=$gitUserEmail" """
            }
        }

        dependencies {
            snapshot(RelativeId("Check_Stage_${this@PublishGradleDistribution.triggerName}_Trigger")) {
            }
        }
    }
}

fun VersionedSettingsBranch.promoteNightlyTaskName(): String = when {
    isMaster -> "promoteNightly"
    isRelease -> "promoteReleaseNightly"
    else -> "promotePatchReleaseNightly"
}
