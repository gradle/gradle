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

import common.KillProcessMode.KILL_ALL_GRADLE_PROCESSES
import configurations.CompileAll
import configurations.FunctionalTest
import configurations.branchesFilterExcluding
import configurations.checkCleanDirUnixLike
import configurations.checkCleanDirWindows
import configurations.enablePullRequestFeature
import jetbrains.buildServer.configs.kotlin.AbsoluteId
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.Requirements
import jetbrains.buildServer.configs.kotlin.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.ui.add
import java.util.Locale

const val PLUGINS_PORTAL_URL_OVERRIDE = "-Dorg.gradle.internal.plugins.portal.url.override=%gradle.plugins.portal.url%"

fun BuildSteps.customGradle(
    init: GradleBuildStep.() -> Unit,
    custom: GradleBuildStep.() -> Unit,
): GradleBuildStep =
    GradleBuildStep(init)
        .apply(custom)
        .also {
            step(it)
        }

/**
 * Adds a [Gradle build step](https://confluence.jetbrains.com/display/TCDL/Gradle)
 * that runs with the Gradle wrapper.
 *
 * @see GradleBuildStep
 */
fun BuildSteps.gradleWrapper(
    buildType: BuildType? = null,
    init: GradleBuildStep.() -> Unit,
): GradleBuildStep =
    customGradle(init) {
        useGradleWrapper = true
        if (buildFile == null) {
            buildFile = "" // Let Gradle detect the build script
        }
        skipConditionally(buildType)
    }

fun Requirements.requiresOs(os: Os) {
    contains("teamcity.agent.jvm.os.name", os.agentRequirement)
}

fun Requirements.requiresArch(
    os: Os,
    arch: Arch,
) {
    if (os == Os.MACOS) {
        contains("teamcity.agent.jvm.os.arch", arch.nameOnMac)
    } else {
        contains("teamcity.agent.jvm.os.arch", arch.nameOnLinuxWindows)
    }
}

fun Requirements.requiresEc2Agent() {
    matches("teamcity.agent.name", "^(ec|EC)2-.*$")
}

fun Requirements.requiresNotEc2Agent() {
    doesNotContain("teamcity.agent.name", "ec2")
    // US region agents have name "EC2-XXX"
    doesNotContain("teamcity.agent.name", "EC2")
}

/**
 * We have some "shared" host where a Linux build agent and a Windows build agent
 * both run on the same bare metal. Some builds require exclusive access to the
 * hardware resources (e.g. performance test).
 */
fun Requirements.requiresNotSharedHost() {
    doesNotContain("agent.host.type", "shared")
}

/**
 * This is an undocumented location that forbids anonymous access.
 * We put artifacts here to avoid accidentally exposing sensitive information publicly.
 */
const val HIDDEN_ARTIFACT_DESTINATION = ".teamcity/gradle-logs"

fun BuildType.applyDefaultSettings(
    os: Os = Os.LINUX,
    arch: Arch = Arch.AMD64,
    buildJvm: Jvm = BuildToolBuildJvm,
    timeout: Int = 30,
    artifactRuleOverride: String? = null,
) {
    val defaultArtifactRules =
        """
        *.psoutput => $HIDDEN_ARTIFACT_DESTINATION
        *.threaddump => $HIDDEN_ARTIFACT_DESTINATION
        build/report-* => $HIDDEN_ARTIFACT_DESTINATION
        build/tmp/teŝt files/** => $HIDDEN_ARTIFACT_DESTINATION/teŝt-files
        build/errorLogs/** => $HIDDEN_ARTIFACT_DESTINATION/errorLogs
        build/reports/configuration-cache/**/configuration-cache-report.html
        subprojects/internal-build-reports/build/reports/incubation/all-incubating.html => incubation-reports
        testing/architecture-test/build/reports/binary-compatibility/report.html => binary-compatibility-reports
        build/reports/dependency-verification/** => dependency-verification-reports
        build/reports/problems/problems-report.html
        """.trimIndent()

    artifactRules = artifactRuleOverride ?: defaultArtifactRules
    paramsForBuildToolBuild(buildJvm, os, arch)
    params {
        // The promotion job doesn't have a branch, so %teamcity.build.branch% doesn't work.
        param("env.BUILD_BRANCH", "%teamcity.build.branch%")
    }

    vcs {
        root(AbsoluteId(VersionedSettingsBranch.fromDslContext().vcsRootId()))
        checkoutMode = CheckoutMode.ON_AGENT
        branchFilter = branchesFilterExcluding()
    }

    features {
        enablePullRequestFeature()
    }

    requirements {
        requiresOs(os)
        requiresArch(os, arch)
    }

    failureConditions {
        if (this@applyDefaultSettings.type != BuildTypeSettings.Type.COMPOSITE) {
            executionTimeoutMin = timeout
        }
        testFailure = true
        supportTestRetry = true
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

    if (os !in listOf(Os.WINDOWS, Os.MACOS)) {
        steps {
            exec {
                name = "EC2_BUILD_CUSTOMIZATIONS"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                path = ".teamcity/scripts/configure_build_env_on_ec2.sh"

                conditions {
                    requiresEc2Agent()
                }
            }
        }
    }
}

fun javaHome(
    jvm: Jvm,
    os: Os,
    arch: Arch = Arch.AMD64,
) = "%${if (os == Os.ALPINE) "linux" else os.name.lowercase()}.${
    jvm.version.name.lowercase().replace(
        "_",
        "",
    )
}.${jvm.vendor.name.lowercase()}.${arch.suffix}%"

fun BuildType.paramsForBuildToolBuild(
    buildJvm: Jvm = BuildToolBuildJvm,
    os: Os,
    arch: Arch = Arch.AMD64,
) {
    params {
        param("env.BOT_TEAMCITY_GITHUB_TOKEN", "%github.bot-teamcity.token%")
        param("env.GRADLE_CACHE_REMOTE_SERVER", "%gradle.cache.remote.server%")

        param("env.JAVA_HOME", javaHome(buildJvm, os, arch))
        param("env.ANDROID_HOME", os.androidHome)
        param("env.ANDROID_SDK_ROOT", os.androidHome)
        param("env.GRADLE_INTERNAL_REPO_URL", "%gradle.internal.repository.url%")
        if (os == Os.MACOS) {
            // Use fewer parallel forks on macOs, since the agents are not very powerful.
            param("maxParallelForks", "2")
        }
        if (os == Os.LINUX || os == Os.MACOS) {
            param("env.LC_ALL", "en_US.UTF-8")
        }
    }
}

fun BuildSteps.checkCleanM2AndAndroidUserHome(
    os: Os = Os.LINUX,
    buildType: BuildType? = null,
) {
    script {
        name = "CHECK_CLEAN_M2_ANDROID_USER_HOME"
        executionMode = BuildStep.ExecutionMode.ALWAYS
        scriptContent =
            if (os == Os.WINDOWS) {
                checkCleanDirWindows("%teamcity.agent.jvm.user.home%\\.m2\\repository") +
                    checkCleanDirWindows("%teamcity.agent.jvm.user.home%\\.m2\\.gradle-enterprise") +
                    checkCleanDirWindows("%teamcity.agent.jvm.user.home%\\.m2\\.develocity") +
                    checkCleanDirWindows(
                        "%teamcity.agent.jvm.user.home%\\.android",
                        false,
                    )
            } else {
                checkCleanDirUnixLike("%teamcity.agent.jvm.user.home%/.m2/repository") +
                    checkCleanDirUnixLike("%teamcity.agent.jvm.user.home%/.m2/.gradle-enterprise") +
                    checkCleanDirUnixLike("%teamcity.agent.jvm.user.home%/.m2/.develocity") +
                    checkCleanDirUnixLike(
                        "%teamcity.agent.jvm.user.home%/.android",
                        false,
                    )
            }
        skipConditionally(buildType)
    }
}

fun BuildStep.onlyRunOnGitHubMergeQueueBranch() {
    conditions {
        matches("teamcity.build.branch", "gh-readonly-queue/.*")
    }
}

fun BuildStep.skipConditionally(buildType: BuildType? = null) {
    // we need to run CompileALl unconditionally because of artifact dependency
    if (buildType !is CompileAll) {
        conditions {
            doesNotEqual("skip.build", "true")
        }
    }
}

fun buildToolGradleParameters(
    isContinue: Boolean = true,
    maxParallelForks: String = "%maxParallelForks%",
): List<String> =
    listOf(
        // We pass the 'maxParallelForks' setting as 'workers.max' to limit the maximum number of executers even
        // if multiple test tasks run in parallel. We also pass it to the Gradle build as a maximum (maxParallelForks)
        // for each test task, such that we are independent of whatever default value is defined in the build itself.
        "-Dorg.gradle.workers.max=$maxParallelForks",
        "-PmaxParallelForks=$maxParallelForks",
        PLUGINS_PORTAL_URL_OVERRIDE,
        "-s",
        "%additional.gradle.parameters%",
        if (isContinue) "--continue" else "",
    )

fun Dependencies.dependsOn(buildTypeId: RelativeId) {
    dependency(buildTypeId) {
        snapshot {
            onDependencyFailure = FailureAction.FAIL_TO_START
            onDependencyCancel = FailureAction.FAIL_TO_START
        }
    }
}

fun Dependencies.compileAllDependency(compileAllId: String) {
    // Compile All has to succeed before anything else is started
    dependsOn(RelativeId(compileAllId))
    // Get the build receipt from sanity check to reuse the timestamp
    artifacts(RelativeId(compileAllId)) {
        id = "ARTIFACT_DEPENDENCY_$compileAllId"
        cleanDestination = true
        artifactRules = "build-receipt.properties => incoming-distributions"
    }
}

fun functionalTestExtraParameters(
    buildScanTags: List<String>,
    os: Os,
    arch: Arch,
    testJvmVersion: String,
    testJvmVendor: String,
): String {
    val buildScanValues =
        mapOf(
            "coverageOs" to os.name.lowercase(),
            "coverageArch" to arch.name.lowercase(),
            "coverageJvmVendor" to testJvmVendor,
            "coverageJvmVersion" to "java$testJvmVersion",
        )
    return (
        listOf(
            "-PtestJavaVersion=$testJvmVersion",
            "-PtestJavaVendor=$testJvmVendor",
        ) +
            buildScanTags.map { buildScanTagParam(it) } +
            buildScanValues.map { buildScanCustomValueParam(it.key, it.value) }
    ).filter { it.isNotBlank() }.joinToString(separator = " ")
}

fun functionalTestParameters(
    os: Os,
    arch: Arch = Arch.AMD64,
): List<String> =
    listOf(
        "-PteamCityBuildId=%teamcity.build.id%",
        os.javaInstallationLocations(arch),
        "-Porg.gradle.java.installations.auto-download=false",
        "-Porg.gradle.java.installations.auto-detect=false",
    )

fun promotionBuildParameters(
    dependencyBuildId: RelativeId,
    extraParameters: String,
    gitUserName: String,
    gitUserEmail: String,
): String =
    listOf(
        "-PcommitId=%dep.$dependencyBuildId.build.vcs.number%",
        extraParameters,
        "\"-PgitUserName=$gitUserName\"",
        "\"-PgitUserEmail=$gitUserEmail\"",
        PLUGINS_PORTAL_URL_OVERRIDE,
        "-DenablePredictiveTestSelection=false",
        "%additional.gradle.parameters%",
    ).joinToString(" ")

/**
 * Align with build-logic/cleanup/src/main/java/gradlebuild/cleanup/services/KillLeakingJavaProcesses.java
 */
enum class KillProcessMode {
    KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS,
    KILL_PROCESSES_STARTED_BY_GRADLE,
    KILL_ALL_GRADLE_PROCESSES,
}

fun BuildSteps.killProcessStep(
    buildType: BuildType?,
    mode: KillProcessMode,
    os: Os,
    arch: Arch = Arch.AMD64,
    executionMode: BuildStep.ExecutionMode = BuildStep.ExecutionMode.DEFAULT,
) {
    script {
        name = mode.toString()
        this.executionMode = executionMode
        scriptContent =
            "\"${
                javaHome(
                    BuildToolBuildJvm,
                    os,
                    arch,
                )
            }/bin/java\" build-logic/cleanup/src/main/java/gradlebuild/cleanup/services/KillLeakingJavaProcesses.java $mode" +
            if (os == Os.WINDOWS) "\nwmic Path win32_process Where \"name='java.exe'\"" else ""
        skipConditionally(buildType)
        if (mode == KILL_ALL_GRADLE_PROCESSES && buildType is FunctionalTest) {
            onlyRunOnGitHubMergeQueueBranch()
        }
    }
}

fun BuildType.killProcessStep(
    mode: KillProcessMode,
    os: Os,
    arch: Arch = Arch.AMD64,
    executionMode: BuildStep.ExecutionMode = BuildStep.ExecutionMode.DEFAULT,
) {
    steps {
        killProcessStep(this@killProcessStep, mode, os, arch, executionMode)
    }
}

fun String.toCapitalized() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun String.toCamelCase() = lowercase().replace(Regex("_[a-z]")) { it.value[1].uppercase() }

/**
 * Define clean up rules for the project.
 * See https://www.jetbrains.com/help/teamcity/teamcity-data-clean-up.html#Clean-up+Rules
 *
 * @param historyDays days number of days to store build history .
 * @param artifactsDays number of days to store artifacts. In the stored history, artifacts older than this number will be cleaned up.
 * @param artifactPatterns patterns for artifacts clean-up. If not specified, all artifacts will be removed.
 */
fun Project.cleanupRule(
    historyDays: Int,
    artifactsDays: Int,
    artifactsPatterns: String? = null,
) {
    features {
        this@cleanupRule.cleanup {
            baseRule {
                history(days = historyDays)
            }
            baseRule {
                artifacts(
                    days = artifactsDays,
                    artifactPatterns = artifactsPatterns,
                )
            }
        }
    }
}
