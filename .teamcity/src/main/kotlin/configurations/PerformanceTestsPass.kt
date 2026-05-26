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

package configurations

import common.Os
import common.applyDefaultSettings
import common.setArtifactRules
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.ReuseBuilds
import model.CIBuildModel
import model.PerformanceTestType
import projects.PerformanceTestProject

class PerformanceTestsPass(
    model: CIBuildModel,
    performanceTestProject: PerformanceTestProject,
) : OsAwareBaseGradleBuildType(
        os = Os.LINUX,
        failStage = performanceTestProject.spec.failsStage,
        init = {
            id("${performanceTestProject.spec.asConfigurationId(model)}_Trigger")
            val performanceTestSpec = performanceTestProject.spec
            name = performanceTestProject.name + " (Trigger)"

            val os = os
            val type = performanceTestSpec.type

            applyDefaultSettings(os)
            params {
                text(
                    "reverse.dep.*.performance.baselines",
                    type.defaultBaselines,
                    display = ParameterDisplay.PROMPT,
                    allowEmpty = true,
                    description = "The baselines you want to run performance tests against. Empty means default baseline.",
                )
                param("env.PERFORMANCE_DB_PASSWORD_TCAGENT", "%performance.db.password.tcagent%")
                param("performance.db.username", "tcagent")
                param("env.PERFORMANCE_CHANNEL", performanceTestSpec.channel())
                text(
                    "performance.baselines",
                    type.defaultBaselines,
                    display = ParameterDisplay.PROMPT,
                    allowEmpty = true,
                    description = "Baselines passed to the aggregate report. Defaults to the type's default baselines.",
                )
            }

            features {
                publishBuildStatusToGithub(model)
            }

            val performanceResultsDir = "perf-results"
            val performanceProjectName = "performance"

            val taskName =
                if (performanceTestSpec.type == PerformanceTestType.FLAKINESS_DETECTION) {
                    "performanceTestFlakinessReport"
                } else {
                    "performanceTestReport"
                }

            setArtifactRules(
                """
testing/$performanceProjectName/build/performance-test-results.zip
""",
            )
            val bucketedPerformanceTests = performanceTestProject.performanceTests.filter { it.testProjects.isNotEmpty() }
            if (bucketedPerformanceTests.isNotEmpty()) {
                // Baselines come from this build's own parameter; per-bucket TeamCity build IDs are derived inside the
                // report task from the surviving perf-results-*.json files. The expected bucket count is passed so the
                // verify task can fail the Trigger when fewer buckets reported than configured.
                val verifyTaskName = "verify${taskName.replaceFirstChar { it.titlecase() }}Buckets"
                gradleRunnerStep(
                    model,
                    ":$performanceProjectName:$taskName :$performanceProjectName:$verifyTaskName",
                    extraParameters =
                        listOf(
                            "-Porg.gradle.performance.branchName" to "%teamcity.build.branch%",
                            "-Porg.gradle.performance.db.url" to "%performance.db.url%",
                            "-Porg.gradle.performance.db.username" to "%performance.db.username%",
                            "-Porg.gradle.performance.expectedBuckets" to bucketedPerformanceTests.size.toString(),
                            "-PperformanceBaselines" to "%performance.baselines%",
                        ).joinToString(" ") { (key, value) -> os.escapeKeyValuePair(key, value) },
                )
            }

            dependencies {
                snapshotDependencies(performanceTestProject.performanceTests) {
                    if (type == PerformanceTestType.FLAKINESS_DETECTION) {
                        reuseBuilds = ReuseBuilds.NO
                    }
                    // Allow the Trigger to run when an upstream bucket is cancelled so the aggregate report is still produced.
                    onDependencyCancel = FailureAction.ADD_PROBLEM
                }
                performanceTestProject.performanceTests.forEachIndexed { index, performanceTest ->
                    if (performanceTest.testProjects.isNotEmpty()) {
                        artifacts(performanceTest.id!!) {
                            id = "ARTIFACT_DEPENDENCY_${performanceTest.id!!}"
                            cleanDestination = true
                            // `?:` marks the rule as optional (TeamCity 2024.03+) so a bucket with no test-results-*.zip
                            // only logs a warning instead of blocking the Trigger from starting.
                            val perfResultArtifactRule =
                                "?:results/performance/build/test-results-*.zip!performance-tests/perf-results*.json => " +
                                    "$performanceResultsDir/${performanceTest.bucketIndex}/"
                            artifactRules =
                                if (index == 0) {
                                    // The artifact rule report/css/*.css => performanceResultsDir is there to clean up the target directory.
                                    // If we don't clean that up there might be leftover json files from other report builds running on the same machine.
                                    """
                                    ?:results/performance/build/test-results-*.zip!performance-tests/report/css/*.css => $performanceResultsDir/
                                    $perfResultArtifactRule
                                    """.trimIndent()
                                } else {
                                    perfResultArtifactRule
                                }
                        }
                    }
                }
            }
        },
    )
