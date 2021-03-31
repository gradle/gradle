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

import configurations.branchesFilterExcluding
import configurations.m2CleanScriptUnixLike
import configurations.m2CleanScriptWindows
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.v2019_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2019_2.Dependencies
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.Requirements
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.v2019_2.ui.add

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

fun Requirements.requiresNoEc2Agent() {
    doesNotContain("teamcity.agent.name", "ec2")
    // US region agents have name "EC2-XXX"
    doesNotContain("teamcity.agent.name", "EC2")
}

const val failedTestArtifactDestination = ".teamcity/gradle-logs"

fun BuildType.applyDefaultSettings(os: Os = Os.LINUX, timeout: Int = 30, versionedSettingsBranch: String? = null) {
    artifactRules = """
        build/report-* => $failedTestArtifactDestination
        build/tmp/test files/** => $failedTestArtifactDestination/test-files
        build/errorLogs/** => $failedTestArtifactDestination/errorLogs
        subprojects/internal-build-reports/build/reports/incubation/all-incubating.html => incubation-reports
        build/reports/dependency-verification/** => dependency-verification-reports
    """.trimIndent()

    vcs {
        root(AbsoluteId("Gradle_Branches_GradlePersonalBranches"))
        checkoutMode = CheckoutMode.ON_AGENT

        branchFilter = when (versionedSettingsBranch) {
            "master" -> branchesFilterExcluding("release")
            "release" -> branchesFilterExcluding("master")
            else -> branchesFilterExcluding()
        }
    }

    requirements {
        requiresOs(os)
    }

    failureConditions {
        if (this@applyDefaultSettings.type != BuildTypeSettings.Type.COMPOSITE) {
            executionTimeoutMin = timeout
        }
        testFailure = false
        add {
            failOnText {
                conditionType = BuildFailureOnText.ConditionType.CONTAINS
                pattern = "%unmaskedFakeCredentials%"
                failureMessage = "This build might be leaking credentials"
                reverse = false
                stopBuildOnFailure = true
            }
        }
    }

    if (os == Os.LINUX || os == Os.MACOS) {
        params {
            param("env.LC_ALL", "en_US.UTF-8")
        }
    }
}

fun BuildSteps.checkCleanM2(os: Os = Os.LINUX) {
    script {
        name = "CHECK_CLEAN_M2"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent = if (os == Os.WINDOWS) m2CleanScriptWindows else m2CleanScriptUnixLike
    }
}

fun buildToolGradleParameters(daemon: Boolean = true, isContinue: Boolean = true): List<String> =
    listOf(
        // We pass the 'maxParallelForks' setting as 'workers.max' to limit the maximum number of executers even
        // if multiple test tasks run in parallel. We also pass it to the Gradle build as a maximum (maxParallelForks)
        // for each test task, such that we are independent of whatever default value is defined in the build itself.
        "-Dorg.gradle.workers.max=%maxParallelForks%",
        "-PmaxParallelForks=%maxParallelForks%",
        "-s",
        if (daemon) "--daemon" else "--no-daemon",
        if (isContinue) "--continue" else ""
    )

fun Dependencies.compileAllDependency(compileAllId: String) {
    // Compile All has to succeed before anything else is started
    dependency(RelativeId(compileAllId)) {
        snapshot {
            onDependencyFailure = FailureAction.CANCEL
            onDependencyCancel = FailureAction.CANCEL
        }
    }
    // Get the build receipt from sanity check to reuse the timestamp
    artifacts(RelativeId(compileAllId)) {
        id = "ARTIFACT_DEPENDENCY_$compileAllId"
        cleanDestination = true
        artifactRules = "build-receipt.properties => incoming-distributions"
    }
}
