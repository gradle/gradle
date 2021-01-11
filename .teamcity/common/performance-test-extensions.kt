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

import configurations.buildScanTag
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script

fun BuildType.applyPerformanceTestSettings(os: Os = Os.LINUX, timeout: Int = 30) {
    applyDefaultSettings(os = os, timeout = timeout)
    artifactRules = """
        build/report-*-performance-tests.zip => .
    """.trimIndent()
    detectHangingBuilds = false
    requirements {
        requiresNoEc2Agent()
    }
    params {
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", os.buildJavaHome())
        param("env.BUILD_BRANCH", "%teamcity.build.branch%")
        param("env.JPROFILER_HOME", os.jprofilerHome)
        param("performance.db.username", "tcagent")
    }
}

fun performanceTestCommandLine(task: String, baselines: String, extraParameters: String = "", os: Os = Os.LINUX) = listOf(
    "$task${if (extraParameters.isEmpty()) "" else " $extraParameters" }",
    "-PperformanceBaselines=$baselines",
    "-PtestJavaVersion=${os.perfTestJavaVersion.major}",
    "-PtestJavaVendor=${os.perfTestJavaVendor}",
    "-Porg.gradle.java.installations.auto-download=false"
) + listOf(
    "-Porg.gradle.performance.branchName" to "%teamcity.build.branch%",
    "-Porg.gradle.performance.db.url" to "%performance.db.url%",
    "-Porg.gradle.performance.db.username" to "%performance.db.username%"
).map { (key, value) -> os.escapeKeyValuePair(key, value) }

const val individualPerformanceTestArtifactRules = """
subprojects/*/build/test-results-*.zip => results
subprojects/*/build/tmp/**/log.txt => failure-logs
subprojects/*/build/tmp/**/profile.log => failure-logs
subprojects/*/build/tmp/**/daemon-*.out.log => failure-logs
"""

fun BuildSteps.killGradleProcessesStep(os: Os) {
    script {
        name = "KILL_GRADLE_PROCESSES"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = os.killAllGradleProcesses
    }
}

// to avoid pathname too long error
fun BuildSteps.substDirOnWindows(os: Os) {
    if (os == Os.WINDOWS) {
        script {
            name = "SETUP_VIRTUAL_DISK_FOR_PERF_TEST"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """subst p: "%teamcity.build.checkoutDir%" """
        }
        cleanBuildLogicBuild("P:/build-logic-commons", os)
        cleanBuildLogicBuild("P:/build-logic", os)
    }
}

fun BuildSteps.removeSubstDirOnWindows(os: Os) {
    if (os == Os.WINDOWS) {
        script {
            name = "REMOVE_VIRTUAL_DISK_FOR_PERF_TEST"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """subst p: /d"""
        }
        cleanBuildLogicBuild("%teamcity.build.checkoutDir%/build-logic-commons", os)
        cleanBuildLogicBuild("%teamcity.build.checkoutDir%/build-logic", os)
    }
}

private fun BuildSteps.cleanBuildLogicBuild(buildDir: String, os: Os) {
    // Gradle detects overlapping outputs when running first on a subst drive and then in the original location.
    // Even when running clean builds on CI, we don't run clean in buildSrc, so there may be stale leftover files there.
    // This means that we need to clean buildSrc before running for the first time on the subst drive
    // and before running the first time on the original location again.
    gradleWrapper {
        name = "CLEAN_${buildDir.toUpperCase().replace("[:/%.]".toRegex(), "_")}"
        tasks = "clean"
        workingDir = buildDir
        executionMode = BuildStep.ExecutionMode.ALWAYS
        gradleWrapperPath = "../"
        gradleParams = (
            buildToolGradleParameters() +
                buildScanTag("PerformanceTest")
            ).joinToString(separator = " ")
    }
}
