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

import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.buildSteps.script

fun BuildType.applyPerformanceTestSettings(os: Os = Os.LINUX, arch: Arch = Arch.AMD64, timeout: Int = 30) {
    applyDefaultSettings(os = os, arch = arch, timeout = timeout)
    artifactRules = """
        build/report-*-performance-tests.zip => .
        build/report-*-performance.zip => $hiddenArtifactDestination
        build/report-*PerformanceTest.zip => $hiddenArtifactDestination
    """.trimIndent()
    detectHangingBuilds = false
    requirements {
        requiresNotEc2Agent()
        requiresNotSharedHost()
    }
    params {
        param("env.JPROFILER_HOME", os.jprofilerHome)
        param("performance.db.username", "tcagent")
    }
}

fun performanceTestCommandLine(
    task: String,
    baselines: String,
    extraParameters: String = "",
    os: Os = Os.LINUX,
    arch: Arch = Arch.AMD64,
    testJavaVersion: String = os.perfTestJavaVersion.major.toString(),
    testJavaVendor: String = os.perfTestJavaVendor.toString(),
) = listOf(
    "$task${if (extraParameters.isEmpty()) "" else " $extraParameters"}",
    "-PperformanceBaselines=$baselines",
    "-PtestJavaVersion=$testJavaVersion",
    "-PtestJavaVendor=$testJavaVendor",
    "-PautoDownloadAndroidStudio=true",
    "-PrunAndroidStudioInHeadlessMode=true",
    "-Porg.gradle.java.installations.auto-download=false",
    os.javaInstallationLocations(arch)
) + listOf(
    "-Porg.gradle.performance.branchName" to "%teamcity.build.branch%",
    "-Porg.gradle.performance.db.url" to "%performance.db.url%",
    "-Porg.gradle.performance.db.username" to "%performance.db.username%"
).map { (key, value) -> os.escapeKeyValuePair(key, value) }

const val individualPerformanceTestArtifactRules = """
testing/*/build/test-results-*.zip => results
testing/*/build/tmp/**/log.txt => failure-logs
testing/*/build/tmp/**/profile.log => failure-logs
testing/*/build/tmp/**/daemon-*.out.log => failure-logs
"""

// to avoid pathname too long error
fun BuildSteps.substDirOnWindows(os: Os) {
    if (os == Os.WINDOWS) {
        script {
            name = "SETUP_VIRTUAL_DISK_FOR_PERF_TEST"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                subst p: /d
                subst p: "%teamcity.build.checkoutDir%"
            """.trimIndent()
            skipConditionally()
        }
    }
}

fun BuildType.cleanUpGitUntrackedFilesAndDirectories() {
    steps {
        script {
            name = "CLEAN_UP_GIT_UNTRACKED_FILES_AND_DIRECTORIES"
            executionMode = BuildStep.ExecutionMode.RUN_ONLY_ON_FAILURE
            scriptContent = "git clean -fdx -e test-splits/ -e .gradle/workspace-id.txt -e \"*.psoutput\""
            skipConditionally()
            onlyRunOnGitHubMergeQueueBranch()
        }
    }
}

fun BuildSteps.removeSubstDirOnWindows(os: Os) {
    if (os == Os.WINDOWS) {
        script {
            name = "REMOVE_VIRTUAL_DISK_FOR_PERF_TEST"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """dir p: && subst p: /d"""
            skipConditionally()
        }
    }
}
