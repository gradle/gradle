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

import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script

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
    os.javaInstallationLocations()
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
        skipConditionally()
    }
}

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
