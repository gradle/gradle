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

package Gradle_Util_Performance.buildTypes

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.builtInRemoteBuildCacheNode
import common.checkCleanM2
import common.compileAllDependency
import common.distributedPerformanceTestParameters
import common.gradleWrapper
import common.performanceTestCommandLine
import configurations.individualPerformanceTestJavaHome
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType

open class AdHocPerformanceTestCoordinator(uuid: String, id: String, os: Os) : BuildType({
    this.uuid = uuid
    id(id)
    name = "AdHoc Performance Test Coordinator - ${os.name.capitalize()}"

    applyPerformanceTestSettings(os = os, timeout = 420)

    maxRunningBuilds = 2

    params {
        param("performance.baselines", "defaults")
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                buildToolGradleParameters(isContinue = false) +
                    builtInRemoteBuildCacheNode.gradleParameters(os) +
                    performanceTestCommandLine(task = "clean distributedPerformanceTests", baselines = "%performance.baselines%", testJavaHome = individualPerformanceTestJavaHome(os), os = os) +
                    distributedPerformanceTestParameters("Gradle_Check_IndividualPerformanceScenarioWorkers${os.name.capitalize()}")
                ).joinToString(separator = " ")
        }
        checkCleanM2(os)
    }

    dependencies {
        compileAllDependency()
    }
})
