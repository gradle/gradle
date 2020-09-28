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

package Gradle_Check.configurations

import Gradle_Check.model.PerformanceTestProjectSpec
import common.Os
import common.applyDefaultSettings
import configurations.*
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.ReuseBuilds
import model.CIBuildModel
import model.PerformanceTestType
import projects.PerformanceTestProject

class PerformanceTestsPass(model: CIBuildModel, performanceTestProject: PerformanceTestProject) : BaseGradleBuildType(model, init = {
    uuid = performanceTestProject.uuid + "_Trigger"
    id = AbsoluteId(uuid)
    val performanceTestSpec = performanceTestProject.spec
    name = performanceTestProject.name + " (Trigger)"

    val os = Os.LINUX
    val type = performanceTestSpec.type

    applyDefaultSettings(os)
    params {
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", os.buildJavaHome())
        param("env.BUILD_BRANCH", "%teamcity.build.branch%")
        param("env.PERFORMANCE_DB_PASSWORD_TCAGENT", "%performance.db.password.tcagent%")
        param("performance.db.username", "tcagent")
        param("performance.channel", performanceTestSpec.channel())
    }

    features {
        publishBuildStatusToGithub(model)
    }

    val performanceResultsDir = "perf-results"
    val performanceProjectName = "performance"

    val taskName = if (performanceTestSpec.type == PerformanceTestType.flakinessDetection)
        "performanceTestFlakinessReport"
    else
        "performanceTestReport"

    artifactRules = """
subprojects/$performanceProjectName/build/performance-test-results.zip
"""
    if (performanceTestProject.performanceTests.any { it.testProjects.isNotEmpty() }) {
        gradleRunnerStep(
            model,
            ":$performanceProjectName:$taskName --channel %performance.channel%",
            extraParameters = listOf(
                "-Porg.gradle.performance.branchName" to "%teamcity.build.branch%",
                "-Porg.gradle.performance.db.url" to "%performance.db.url%",
                "-Porg.gradle.performance.db.username" to "%performance.db.username%"
            ).joinToString(" ") { (key, value) -> os.escapeKeyValuePair(key, value) } +
                explicitToolchains(os.buildJavaHome()).joinToString(" ")
        )
    }

    dependencies {
        snapshotDependencies(performanceTestProject.performanceTests) {
            if (type == PerformanceTestType.flakinessDetection) {
                reuseBuilds = ReuseBuilds.NO
            }
        }
        performanceTestProject.performanceTests.forEachIndexed { index, performanceTest ->
            if (performanceTest.testProjects.isNotEmpty()) {
                artifacts(performanceTest.id!!) {
                    id = "ARTIFACT_DEPENDENCY_${performanceTest.id!!}"
                    cleanDestination = true
                    val perfResultArtifactRule = """results/performance/build/test-results-*.zip!performance-tests/perf-results*.json => $performanceResultsDir/${performanceTest.bucketIndex}/"""
                    artifactRules = if (index == 0) {
                        // The artifact rule report/css/*.css => performanceResultsDir is there to clean up the target directory.
                        // If we don't clean that up there might be leftover json files from other report builds running on the same machine.
                        """
                            results/performance/build/test-results-*.zip!performance-tests/report/css/*.css => $performanceResultsDir/
                            $perfResultArtifactRule
                        """.trimIndent()
                    } else {
                        perfResultArtifactRule
                    }
                }
            }
        }
    }
}) {
    val performanceSpec: PerformanceTestProjectSpec = performanceTestProject.spec
}
