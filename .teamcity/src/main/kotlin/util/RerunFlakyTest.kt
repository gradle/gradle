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
import common.functionalTestExtraParameters
import common.functionalTestParameters
import common.gradleWrapper
import common.killProcessStep
import configurations.CompileAll
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay

class RerunFlakyTest(os: Os) : BuildType({
    val id = "Util_RerunFlakyTest${os.asName()}"
    name = "Rerun Flaky Test - ${os.asName()}"
    description = "Allows you to rerun a selected flaky test 10 times"
    id(id)
    val testJvmVendorParameter = "testJavaVendor"
    val testJvmVersionParameter = "testJavaVersion"
    val testTaskParameterName = "testTask"
    val testNameParameterName = "testName"
    val testTaskOptionsParameterName = "testTaskOptions"
    val daemon = true
    applyDefaultSettings(os, BuildToolBuildJvm, 30)
    val extraParameters = functionalTestExtraParameters("RerunFlakyTest", os, "%$testJvmVersionParameter%", "%$testJvmVendorParameter%")
    val parameters = (
        buildToolGradleParameters(daemon) +
            listOf(extraParameters) +
            functionalTestParameters(os)
        ).joinToString(separator = " ")

    killProcessStep("KILL_LEAKED_PROCESSES_FROM_PREVIOUS_BUILDS", daemon, os)
    steps {
        gradleWrapper {
            name = "SHOW_TOOLCHAINS"
            tasks = "javaToolchains"
            gradleParams = parameters
        }
    }
    (1..10).forEach { idx ->
        steps {
            gradleWrapper {
                name = "GRADLE_RUNNER_$idx"
                tasks = "%$testTaskParameterName% --rerun --tests %$testNameParameterName% %$testTaskOptionsParameterName%"
                gradleParams = parameters
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
            testNameParameterName,
            """org.gradle.api.tasks.CachedTaskExecutionIntegrationTest.outputs*are*correctly*loaded*from*cache""",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "The name of the test to run, as should be passed to --tests. Can't contain spaces since there are problems with Teamcity's escaping, you can use * instead."
        )
        text(
            testJvmVersionParameter,
            "11",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Java version to run the test with"
        )
        text(
            testJvmVendorParameter,
            "openjdk",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Java vendor to run the test with"
        )
        text(
            testTaskOptionsParameterName,
            "",
            display = ParameterDisplay.PROMPT,
            allowEmpty = true,
            description = "Additional options for the test task to run"
        )
    }

    dependencies {
        compileAllDependency(CompileAll.buildTypeId("Check"))
    }
})
