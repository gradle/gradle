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

import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay

object StartReleaseCycle : BasePromotionBuildType(vcsRoot = Gradle_Promotion.vcsRoots.Gradle_Promotion__master_) {
    init {
        uuid = "355487d7-45b9-4387-9fc5-713e7683e6d0"
        id("Gradle_Promotion_StartReleaseCycle")
        name = "Master - Start Release Cycle"
        description = "Promotes a successful build on master as the start of a new release cycle on the release branch"

        artifactRules = """
        incoming-build-receipt/build-receipt.properties => incoming-build-receipt
    """.trimIndent()

        params {
            text("gitUserEmail", "", label = "Git user.email Configuration", description = "Enter the git 'user.email' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
            text("confirmationCode", "", label = "Confirmation Code", description = "Enter the value 'startCycle' (no quotes) to confirm the promotion", display = ParameterDisplay.PROMPT, allowEmpty = false)
            text("gitUserName", "", label = "Git user.name Configuration", description = "Enter the git 'user.name' configuration to commit change under", display = ParameterDisplay.PROMPT, allowEmpty = true)
        }

        steps {
            gradleWrapper {
                name = "Promote"
                tasks = "clean promoteStartReleaseCycle"
                useGradleWrapper = true
                gradleParams = """-PuseBuildReceipt -PconfirmationCode=%confirmationCode% "-PgitUserName=%gitUserName%" "-PgitUserEmail=%gitUserEmail%" -Igradle/buildScanInit.gradle"""
                param("org.jfrog.artifactory.selectedDeployableServer.defaultModuleVersionConfiguration", "GLOBAL")
            }
        }

        dependencies {
            artifacts(AbsoluteId("Gradle_Check_Stage_ReadyforNightly_Trigger")) {
                buildRule = lastSuccessful("master")
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-build-receipt/"
            }
        }
    }
}
