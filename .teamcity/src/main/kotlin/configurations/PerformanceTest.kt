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

package configurations

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.cleanUpPerformanceBuildDir
import common.gradleWrapper
import common.individualPerformanceTestArtifactRules
import common.killGradleProcessesStep
import common.performanceTestCommandLine
import common.removeSubstDirOnWindows
import common.substDirOnWindows
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import model.CIBuildModel
import model.PerformanceTestBuildSpec
import model.PerformanceTestType
import model.Stage

class PerformanceTest(
    model: CIBuildModel,
    stage: Stage,
    performanceTestBuildSpec: PerformanceTestBuildSpec,
    description: String,
    performanceSubProject: String,
    val testProjects: List<String>,
    val bucketIndex: Int,
    extraParameters: String = "",
    performanceTestTaskSuffix: String = "PerformanceTest",
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(
    stage = stage,
    init = {
        this.id(performanceTestBuildSpec.asConfigurationId(model, "bucket${bucketIndex + 1}"))
        this.name = "$description${if (performanceTestBuildSpec.withoutDependencies) " (without dependencies)" else ""}"
        val type = performanceTestBuildSpec.type
        val os = performanceTestBuildSpec.os
        val performanceTestTaskNames = getPerformanceTestTaskNames(performanceSubProject, testProjects, performanceTestTaskSuffix)
        applyPerformanceTestSettings(os = os, arch = os.defaultArch, timeout = type.timeout)
        artifactRules = individualPerformanceTestArtifactRules

        params {
            text(
                "performance.baselines",
                type.defaultBaselines,
                display = ParameterDisplay.PROMPT,
                allowEmpty = true,
                description = "The baselines you want to run performance tests against. Empty means default baseline."
            )
            param("performance.channel", performanceTestBuildSpec.channel())
            param("env.PERFORMANCE_DB_PASSWORD_TCAGENT", "%performance.db.password.tcagent%")
            when (os) {
                Os.WINDOWS -> param("env.PATH", "%env.PATH%;C:/Program Files/7-zip")
                else -> param("env.PATH", "%env.PATH%:/opt/swift/4.2.3/usr/bin:/opt/swift/4.2.4-RELEASE-ubuntu18.04/usr/bin")
            }
        }
        failureConditions {
            // We have test-retry to handle the crash in tests
            javaCrash = false
            // We want to see the flaky tests for flakiness detection
            supportTestRetry = (performanceTestBuildSpec.type != PerformanceTestType.flakinessDetection)
        }
        if (testProjects.isNotEmpty()) {
            steps {
                preBuildSteps()
                killGradleProcessesStep(os)
                substDirOnWindows(os)

                repeat(if (performanceTestBuildSpec.type == PerformanceTestType.flakinessDetection) 2 else 1) { repeatIndex: Int ->
                    gradleWrapper {
                        name = "GRADLE_RUNNER${if (repeatIndex == 0) "" else "_2"}"
                        tasks = ""
                        workingDir = os.perfTestWorkingDir
                        gradleParams = (
                            performanceTestCommandLine(
                                "${if (repeatIndex == 0) "clean" else ""} ${performanceTestTaskNames.joinToString(" ") { "$it --channel %performance.channel% ${type.extraParameters}" }}",
                                "%performance.baselines%",
                                extraParameters,
                                os
                            ) + "-DenableTestDistribution=%enableTestDistribution%" +
                                buildToolGradleParameters() +
                                buildScanTag("PerformanceTest")
                            ).joinToString(separator = " ")
                    }
                }
                cleanUpPerformanceBuildDir(os)
                removeSubstDirOnWindows(os)
                checkCleanM2AndAndroidUserHome(os)
            }
        }

        applyDefaultDependencies(model, this, !performanceTestBuildSpec.withoutDependencies)
    }
)

fun getPerformanceTestTaskNames(performanceSubProject: String, testProjects: List<String>, performanceTestTaskSuffix: String): List<String> {
    return testProjects.map {
        ":$performanceSubProject:$it$performanceTestTaskSuffix"
    }
}
