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

import common.INDIVIDUAL_PERFORAMCE_TEST_ARTIFACT_RULES
import common.KillProcessMode.KILL_ALL_GRADLE_PROCESSES
import common.KillProcessMode.KILL_PROCESSES_STARTED_BY_GRADLE
import common.Os
import common.applyPerformanceTestSettings
import common.buildScanTagParam
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.getBuildScanCustomValueParam
import common.gradleWrapper
import common.killProcessStep
import common.performanceTestCommandLine
import common.removeSubstDirOnWindows
import common.substDirOnWindows
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
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
    preBuildSteps: BuildSteps.() -> Unit = {},
) : BaseGradleBuildType(
        stage = stage,
        init = {
            this.id(performanceTestBuildSpec.asConfigurationId(model, "bucket${bucketIndex + 1}"))
            this.name = description
            val type = performanceTestBuildSpec.type
            val os = performanceTestBuildSpec.os
            val arch = performanceTestBuildSpec.arch
            val buildTypeThis = this
            val performanceTestTaskNames = getPerformanceTestTaskNames(performanceSubProject, testProjects, performanceTestTaskSuffix)
            applyPerformanceTestSettings(os = os, arch = arch, timeout = type.timeout)
            artifactRules = INDIVIDUAL_PERFORAMCE_TEST_ARTIFACT_RULES

            params {
                text(
                    "performance.baselines",
                    type.defaultBaselines,
                    display = ParameterDisplay.PROMPT,
                    allowEmpty = true,
                    description = "The baselines you want to run performance tests against. Empty means default baseline.",
                )
                param("env.PERFORMANCE_CHANNEL", performanceTestBuildSpec.channel())
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
                supportTestRetry = (performanceTestBuildSpec.type != PerformanceTestType.FLAKINESS_DETECTION)
            }
            if (testProjects.isNotEmpty()) {
                steps {
                    preBuildSteps()
                    killProcessStep(buildTypeThis, KILL_ALL_GRADLE_PROCESSES, os)
                    substDirOnWindows(os)

                    repeat(if (performanceTestBuildSpec.type == PerformanceTestType.FLAKINESS_DETECTION) 2 else 1) { repeatIndex: Int ->
                        gradleWrapper {
                            name = "GRADLE_RUNNER${if (repeatIndex == 0) "" else "_2"}"
                            tasks = ""
                            workingDir = os.perfTestWorkingDir

                            val typeExtraParameters = if (type.extraParameters.isEmpty()) "" else " ${type.extraParameters}"

                            gradleParams =
                                (
                                    performanceTestCommandLine(
                                        "${if (repeatIndex == 0) "clean" else ""} ${performanceTestTaskNames.joinToString(
                                            " ",
                                        ) { "$it$typeExtraParameters" }}",
                                        "%performance.baselines%",
                                        extraParameters,
                                        os,
                                        arch,
                                    ) + "-DenableTestDistribution=%enableTestDistribution%" +
                                        buildToolGradleParameters() +
                                        stage.getBuildScanCustomValueParam() +
                                        buildScanTagParam("PerformanceTest")
                                ).joinToString(separator = " ")
                        }
                    }
                    removeSubstDirOnWindows(os)
                    killProcessStep(buildTypeThis, KILL_PROCESSES_STARTED_BY_GRADLE, os, executionMode = BuildStep.ExecutionMode.ALWAYS)
                    checkCleanM2AndAndroidUserHome(os)
                }
            }

            applyDefaultDependencies(model, this)
        },
    )

fun getPerformanceTestTaskNames(
    performanceSubProject: String,
    testProjects: List<String>,
    performanceTestTaskSuffix: String,
): List<String> =
    testProjects.map {
        ":$performanceSubProject:$it$performanceTestTaskSuffix"
    }
