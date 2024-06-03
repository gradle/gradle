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

import common.Arch
import common.BuildToolBuildJvm
import common.JvmVendor
import common.JvmVersion
import common.KillProcessMode.KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS
import common.KillProcessMode.KILL_PROCESSES_STARTED_BY_GRADLE
import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.compileAllDependency
import common.functionalTestExtraParameters
import common.functionalTestParameters
import common.gradleWrapper
import common.killProcessStep
import configurations.CompileAll
import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.ParameterDisplay

class RerunFlakyTest(os: Os, arch: Arch = Arch.AMD64) : BuildType({
    val id = "Util_RerunFlakyTest${os.asName()}${arch.asName()}"
    name = "Rerun Flaky Test - ${os.asName()} ${arch.asName()}"
    description = "Allows you to rerun a selected flaky test 10 times"
    id(id)
    val testJvmVendorParameter = "testJavaVendor"
    val testJvmVersionParameter = "testJavaVersion"
    val testTaskParameterName = "testTask"
    val testNameParameterName = "testName"
    val testTaskOptionsParameterName = "testTaskOptions"
    val daemon = true
    applyDefaultSettings(os, arch, buildJvm = BuildToolBuildJvm, timeout = 0)

    // Show all failed tests here, since that is what we are interested in
    failureConditions.supportTestRetry = false

    val extraParameters = functionalTestExtraParameters("RerunFlakyTest", os, arch, "%$testJvmVersionParameter%", "%$testJvmVendorParameter%")
    val parameters = (
        buildToolGradleParameters(daemon) +
            listOf(extraParameters) +
            functionalTestParameters(os, arch)
        ).joinToString(separator = " ")

    killProcessStep(KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS, os, arch)

    (1..10).forEach { idx ->
        steps {
            gradleWrapper {
                name = "GRADLE_RUNNER_$idx"
                tasks = "%$testTaskParameterName% -PrerunAllTests --tests %$testNameParameterName% %$testTaskOptionsParameterName%"
                gradleParams = parameters
                executionMode = BuildStep.ExecutionMode.ALWAYS
            }
        }
        killProcessStep(KILL_PROCESSES_STARTED_BY_GRADLE, os, arch, BuildStep.ExecutionMode.ALWAYS)
    }

    steps {
        checkCleanM2AndAndroidUserHome(os)
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
            testNameParameterName,
            """org.gradle.api.tasks.CachedTaskExecutionIntegrationTest.outputs*are*correctly*loaded*from*cache""",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "The name of the test to run, as should be passed to --tests. Can't contain spaces since there are problems with Teamcity's escaping, you can use * instead."
        )
        text(
            testJvmVersionParameter,
            JvmVersion.java11.major.toString(),
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Java version to run the test with"
        )
        select(
            testJvmVendorParameter,
            JvmVendor.openjdk.name,
            display = ParameterDisplay.PROMPT,
            description = "Java vendor to run the test with",
            options = JvmVendor.values().map { it.displayName to it.name }
        )
        text(
            testTaskOptionsParameterName,
            "",
            display = ParameterDisplay.PROMPT,
            allowEmpty = true,
            description = "Additional options for the test task to run (`-PrerunAllTests` is already added implicitly)"
        )
    }

    dependencies {
        compileAllDependency(CompileAll.buildTypeId("Check"))
    }
})
