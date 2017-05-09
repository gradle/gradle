/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.buildcache

import org.gradle.launcher.daemon.configuration.GradleProperties
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.fixture.BuildExperimentInvocationInfo

import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.MEASUREMENT
import static org.gradle.performance.fixture.BuildExperimentRunner.Phase.WARMUP
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

class AbstractTaskOutputCacheJavaPerformanceTest extends AbstractCrossVersionPerformanceTest{
    int firstWarmupWithCache = 1

    def setup() {
        /*
         * Since every second build is a 'clean', we need more iterations
         * than usual to get reliable results.
         */
        runner.warmUpRuns = 10
        runner.runs = 26
        runner.setupCleanupOnOddRounds()
        runner.args = ["-D${GradleProperties.BUILD_CACHE_PROPERTY}=true"]
        runner.minimumVersion = "3.5"
        runner.targetVersions = ["3.5-20170302014333+0000"]
    }

    /**
     * In order to compare the different cache backends we define the scenarios for the
     * tests here.
     */
    def getScenarios() {
        [
            [LARGE_MONOLITHIC_JAVA_PROJECT, 'assemble'],
            [LARGE_JAVA_MULTI_PROJECT, 'assemble']
        ]
    }

    static boolean isLastRun(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber == invocationInfo.iterationMax && invocationInfo.phase == MEASUREMENT
    }

    boolean isRunWithCache(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber >= firstWarmupWithCache || invocationInfo.phase == MEASUREMENT
    }

    boolean isFirstRunWithCache(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber == firstWarmupWithCache && invocationInfo.phase == WARMUP
    }

    static boolean isCleanupRun(BuildExperimentInvocationInfo invocationInfo) {
        invocationInfo.iterationNumber % 2 == 1
    }
}
