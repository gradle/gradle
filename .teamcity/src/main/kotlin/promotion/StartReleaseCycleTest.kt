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

import common.PLUGINS_PORTAL_URL_OVERRIDE
import common.VersionedSettingsBranch
import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import vcsroots.gradlePromotionBranches

object StartReleaseCycleTest : BasePromotionBuildType(vcsRootId = gradlePromotionBranches, cleanCheckout = false) {
    init {
        id("Promotion_AllBranchesStartReleaseCycleTest")
        name = "Start Release Cycle Test"
        description = "Test for Start Release Cycle pipeline"

        steps {
            gradleWrapper {
                name = "PromoteTest"
                tasks = "clean promoteStartReleaseCycle"
                useGradleWrapper = true
                gradleParams =
                    """-PconfirmationCode=startCycle -PtestRun=1 "-PgitUserName=test" "-PgitUserEmail=test@example.com" $PLUGINS_PORTAL_URL_OVERRIDE"""
            }
        }

        val enableTriggers = VersionedSettingsBranch.fromDslContext().enableVcsTriggers
        triggers {
            vcs {
                branchFilter = "+:master"
                enabled = enableTriggers
            }
            schedule {
                schedulingPolicy =
                    daily {
                        hour = 3
                    }
                branchFilter = "+:master"
                triggerBuild = always()
                withPendingChangesOnly = false
                enabled = enableTriggers
            }
        }
    }
}
