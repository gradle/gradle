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

import configurations.buildJavaHome
import configurations.coordinatorPerformanceTestJavaHome
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType

fun BuildType.applyPerformanceTestSettings(os: Os = Os.linux, timeout: Int = 30) {
    applyDefaultSettings(os = os, timeout = timeout)
    artifactRules = """
        build/report-*-performance-tests.zip => .
    """.trimIndent()
    detectHangingBuilds = false
    requirements {
        doesNotContain("teamcity.agent.name", "ec2")
    }
    params {
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", buildJavaHome)
        param("env.BUILD_BRANCH", "%teamcity.build.branch%")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
    }
}

fun performanceTestCommandLine(task: String, baselines: String, extraParameters: String = "", testJavaHome: String = coordinatorPerformanceTestJavaHome) = listOf(
        "$task --baselines $baselines $extraParameters",
        "-x prepareSamples",
        "-Porg.gradle.performance.branchName=%teamcity.build.branch%",
        "-Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%",
        "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot%",
        "-PtestJavaHome=$testJavaHome"
)

fun distributedPerformanceTestParameters(workerId: String = "Gradle_Check_IndividualPerformanceScenarioWorkersLinux") = listOf(
        "-Porg.gradle.performance.buildTypeId=${workerId} -Porg.gradle.performance.workerTestTaskName=fullPerformanceTest -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id%"
)

