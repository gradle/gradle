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

package common

import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2018_2.Requirements
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.GradleBuildStep

fun BuildSteps.customGradle(init: GradleBuildStep.() -> Unit, custom: GradleBuildStep.() -> Unit): GradleBuildStep =
    GradleBuildStep(init)
        .apply(custom)
        .also { step(it) }

/**
 * Adds a [Gradle build step](https://confluence.jetbrains.com/display/TCDL/Gradle)
 * that runs with the Gradle wrapper.
 *
 * @see GradleBuildStep
 */
fun BuildSteps.gradleWrapper(init: GradleBuildStep.() -> Unit): GradleBuildStep =
    customGradle(init) {
        useGradleWrapper = true
        if (buildFile == null) {
            buildFile = "" // Let Gradle detect the build script
        }
    }

fun Requirements.requiresOs(os: Os) {
    contains("teamcity.agent.jvm.os.name", os.agentRequirement)
}
