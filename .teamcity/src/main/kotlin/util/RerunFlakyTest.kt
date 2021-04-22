/*
 * Copyright 2021 the original author or authors.
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

package util

import common.BuildToolBuildJvm
import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.compileAllDependency
import common.gradleWrapper
import common.killProcessStep
import configurations.CompileAll
import configurations.buildScanCustomValue
import configurations.buildScanTag
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay

class RerunFlakyTest(os: Os) : BuildType({
    val id = "Util_RerunFlakyTest${os.asName()}"
    name = "Rerun Flaky Test - ${os.asName()}"
    description = "Allows you to rerun a selected flaky test 10 times"
    id(id)
    val testJavaVendorParameter = "testJavaVendor"
    val testJavaVersionParameter = "testJavaVersion"
    val testTaskParameterName = "testTask"
    val testTaskOptionsParameterName = "testTaskOptions"
    val buildScanTags = listOf("RerunFlakyTest")
    val buildScanValues = mapOf(
        "coverageOs" to os.name.toLowerCase(),
        "coverageJvmVendor" to "%$testJavaVendorParameter%",
        "coverageJvmVersion" to "%$testJavaVersionParameter%"
    )
    val daemon = true
    applyDefaultSettings(os, BuildToolBuildJvm, 30)
    val extraParameters = (listOf(
        "-PtestJavaVersion=%$testJavaVersionParameter%",
        "-PtestJavaVendor=%$testJavaVendorParameter%") +
        buildScanTags.map { buildScanTag(it) } +
        buildScanValues.map { buildScanCustomValue(it.key, it.value) }
        ).filter { it.isNotBlank() }.joinToString(separator = " ")

    killProcessStep("KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS", daemon, os)
    steps {
        gradleWrapper {
            name = "SHOW_TOOLCHAINS"
            tasks = "javaToolchains"
            gradleParams = (
                buildToolGradleParameters(daemon) +
                    listOf(extraParameters) +
                    "-PteamCityBuildId=%teamcity.build.id%" +
                    buildScanTags.map { buildScanTag(it) } +
                    os.javaInstallationLocations() +
                    "-Porg.gradle.java.installations.auto-download=false"
                ).joinToString(separator = " ")
        }
    }
    (1..10).forEach { idx ->
        steps {
            gradleWrapper {
                name = "GRADLE_RUNNER_$idx"
                tasks = "%$testTaskParameterName% --rerun %$testTaskOptionsParameterName%"
                gradleParams = (
                    buildToolGradleParameters(daemon) +
                        listOf(extraParameters) +
                        "-PteamCityBuildId=%teamcity.build.id%" +
                        buildScanTags.map { buildScanTag(it) } +
                        os.javaInstallationLocations() +
                        "-Porg.gradle.java.installations.auto-download=false"
                    ).joinToString(separator = " ")
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }
        killProcessStep("KILL_PROCESSES_STARTED_BY_GRADLE", daemon, os)
    }
    steps {
        checkCleanM2(os)
    }

    params {
        text(
            testTaskParameterName,
            ":core:embeddedIntegTest",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "The test task you want to run"
        )
        text(
            testTaskOptionsParameterName,
            """--tests "org.gradle.api.tasks.CachedTaskExecutionIntegrationTest.outputs are correctly loaded from cache"""",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Options for the test task to run, like e.g. the test filter"
        )
        text(
            testJavaVersionParameter,
            "11",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Java version to run the test with"
        )
        text(
            testJavaVendorParameter,
            "openjdk",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Java vendor to run the test with"
        )
    }

    dependencies {
        compileAllDependency(CompileAll.buildTypeId("Check"))
    }
})
