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

import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import vcsroots.gradlePromotionMaster

abstract class PublishGradleDistributionBothSteps(
    // The branch to be promoted
    promotedBranch: String,
    task: String,
    triggerName: String,
    gitUserName: String = "bot-teamcity",
    gitUserEmail: String = "bot-teamcity@gradle.com",
    extraParameters: String = "",
    vcsRootId: String = gradlePromotionMaster
) : BasePublishGradleDistribution(promotedBranch, task, triggerName, gitUserName, gitUserEmail, extraParameters, vcsRootId) {
    init {
        steps {
            buildStep1(extraParameters, gitUserName, gitUserEmail, triggerName)
            buildStep2(extraParameters, gitUserName, gitUserEmail, triggerName, task)
        }
    }
}

fun BuildSteps.buildStep1(extraParameters: String, gitUserName: String, gitUserEmail: String, triggerName: String) {
    gradleWrapper {
        name = "Promote"
        tasks = "uploadAll"
        gradleParams = """-PcommitId=%dep.${RelativeId("Check_Stage_${triggerName}_Trigger")}.build.vcs.number% $extraParameters "-PgitUserName=$gitUserName" "-PgitUserEmail=$gitUserEmail" """
    }
}

fun BuildSteps.buildStep2(extraParameters: String, gitUserName: String, gitUserEmail: String, triggerName: String, task: String) {
    gradleWrapper {
        name = "Promote"
        tasks = task
        gradleParams = """-PcommitId=%dep.${RelativeId("Check_Stage_${triggerName}_Trigger")}.build.vcs.number% $extraParameters "-PgitUserName=$gitUserName" "-PgitUserEmail=$gitUserEmail" """
    }
}
