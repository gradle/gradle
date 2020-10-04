/*
 * Copyright 2020 the original author or authors.
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

package Gradle_Check.configurations

import Gradle_Check.model.PerformanceTestCoverage
import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.gradleWrapper
import common.individualPerformanceTestArtifactRules
import common.killGradleProcessesStep
import common.performanceTestCommandLine
import common.removeSubstDirOnWindows
import common.substDirOnWindows
import configurations.BaseGradleBuildType
import configurations.applyDefaultDependencies
import configurations.buildScanTag
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import model.CIBuildModel
import model.Stage

class PerformanceTest(
    model: CIBuildModel,
    stage: Stage,
    performanceTestCoverage: PerformanceTestCoverage,
    description: String,
    performanceSubProject: String,
    val testProjects: List<String>,
    val bucketIndex: Int,
    extraParameters: String = "",
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(
    model,
    stage = stage,
    init = {
        this.uuid = performanceTestCoverage.asConfigurationId(model, "bucket${bucketIndex + 1}")
        this.id = AbsoluteId(uuid)
        this.name = "$description${if (performanceTestCoverage.withoutDependencies) " (without dependencies)" else ""}"
        val type = performanceTestCoverage.type
        val os = performanceTestCoverage.os
        val performanceTestTaskNames = getPerformanceTestTaskNames(performanceSubProject, testProjects)
        applyPerformanceTestSettings(os = os, timeout = type.timeout)
        artifactRules = individualPerformanceTestArtifactRules

        params {
            param("performance.baselines", type.defaultBaselines)
            param("performance.channel", performanceTestCoverage.channel())
            param("env.ANDROID_HOME", os.androidHome)
            when (os) {
                Os.WINDOWS -> param("env.PATH", "%env.PATH%;C:/Program Files/7-zip")
                else -> param("env.PATH", "%env.PATH%:/opt/swift/4.2.3/usr/bin")
            }
        }
        if (testProjects.isNotEmpty()) {
            steps {
                preBuildSteps()
                killGradleProcessesStep(os)
                substDirOnWindows(os, model.parentBuildCache)

                gradleWrapper {
                    name = "GRADLE_RUNNER"
                    tasks = ""
                    workingDir = os.perfTestWorkingDir
                    gradleParams = (
                        performanceTestCommandLine(
                            "clean ${performanceTestTaskNames.joinToString(" ") { "$it --channel %performance.channel% ${type.extraParameters}" }}",
                            "%performance.baselines%",
                            extraParameters,
                            os
                        ) +
                            buildToolGradleParameters(isContinue = false) +
                            buildScanTag("PerformanceTest") +
                            model.parentBuildCache.gradleParameters(os)
                        ).joinToString(separator = " ")
                }
                removeSubstDirOnWindows(os, model.parentBuildCache)
                checkCleanM2(os)
            }
        }

        applyDefaultDependencies(model, this, !performanceTestCoverage.withoutDependencies)
    }
)

fun getPerformanceTestTaskNames(performanceSubProject: String, testProjects: List<String>): List<String> {
    return testProjects.map {
        ":$performanceSubProject:${it}PerformanceTest"
    }
}
