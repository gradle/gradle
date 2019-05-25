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

import common.Os
import common.builtInRemoteBuildCacheNode
import common.gradleWrapper
import common.requiresOs
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

abstract class PublishGradleDistribution(
    branch: String,
    task: String,
    val triggerName: String,
    gitUserName: String = "Gradleware Git Bot",
    gitUserEmail: String = "gradlewaregitbot@gradleware.com",
    extraParameters: String = "",
    vcsRoot: GitVcsRoot = Gradle_Promotion.vcsRoots.Gradle_Promotion__master_
) : BasePromotionBuildType(vcsRoot = vcsRoot) {

    init {
        artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
        **/build/git-checkout/build/build-receipt.properties
        **/build/distributions/*.zip => promote-build-distributions
        **/build/website-checkout/data/releases.xml
        **/build/git-checkout/build/reports/integTest/** => distribution-tests
        **/smoke-tests/build/reports/tests/** => post-smoke-tests
    """.trimIndent()

        steps {
            gradleWrapper {
                name = "Promote"
                tasks = task
                gradleParams = """-PuseBuildReceipt $extraParameters "-PgitUserName=$gitUserName" "-PgitUserEmail=$gitUserEmail" -Igradle/buildScanInit.gradle ${builtInRemoteBuildCacheNode.gradleParameters(Os.linux).joinToString(" ")}"""
            }
        }
        dependencies {
            artifacts(AbsoluteId("Gradle_Check_Stage_${this@PublishGradleDistribution.triggerName}_Trigger")) {
                buildRule = lastSuccessful(branch)
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-build-receipt/"
            }
        }

        requirements {
            requiresOs(Os.linux)
        }
    }
}

fun String.promoteNightlyTaskName(): String = "promote${if (this == "master") "" else capitalize()}Nightly"
