/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.performance.results.BuildDisplayInfo
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossBuildPerformanceTestHistory
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.CrossVersionPerformanceTestHistory
import org.gradle.performance.results.GradleVsMavenBuildPerformanceResults
import org.gradle.performance.results.MeasuredOperationList
import org.gradle.performance.results.PerformanceExperiment
import org.gradle.performance.results.PerformanceScenario
import org.gradle.performance.results.PerformanceTestHistory
import spock.lang.Specification

abstract class ResultSpecification extends Specification {
    final String channel = 'commits'

    CrossVersionPerformanceResults crossVersionResults(Map<String, ?> options = [:]) {
        def results = new CrossVersionPerformanceResults()
        results.testClass = "org.gradle.performance.MyPerformanceTest"
        results.testId = "test-id"
        results.previousTestIds = []
        results.testProject = "test-project"
        results.tasks = ["build"]
        results.cleanTasks = ["clean"]
        results.args = []
        results.gradleOpts = []
        results.daemon = false
        results.operatingSystem = "some os"
        results.host = "me"
        results.jvm = "java 6"
        results.versionUnderTest = "1.7-rc-1"
        results.vcsBranch = "master"
        results.vcsCommits = ['123456']
        results.channel = channel
        results.startTime = new Date().time
        options.each { key, value -> results."$key" = value }
        return results
    }

    CrossBuildPerformanceResults crossBuildResults(Map<String, ?> options = [:]) {
        def results = new CrossBuildPerformanceResults(
                testClass: "org.gradle.performance.MyCrossBuildPerformanceTest",
                testId: "test-id",
                testGroup: "test-group",
                testProject: "test-project",
                jvm: "java 7",
                versionUnderTest: "Gradle 1.0",
                operatingSystem: "windows",
                host: "me",
                vcsBranch: "master",
                vcsCommits: ["abcdef"],
                channel: channel,
                startTime: new Date().time
        )
        options.each { key, value -> results."$key" = value }
        return results
    }

    GradleVsMavenBuildPerformanceResults gradleVsMavenBuildResults(Map<String, ?> options = [:]) {
        def results = new GradleVsMavenBuildPerformanceResults(
                testClass: "org.gradle.performance.MyGradleVsMavenPerformanceTest",
                testId: "test-id",
                testProject: 'test-project',
                testGroup: "test-group",
                jvm: "java 7",
                versionUnderTest: "Gradle 1.0",
                operatingSystem: "windows",
                host: "me",
                startTime: 100,
                vcsBranch: "master",
                vcsCommits: ["abcdef"]
        )
        options.each { key, value -> results."$key" = value }
        return results
    }

    MeasuredOperation operation(Map<String, Object> args = [:]) {
        def operation = new MeasuredOperation()
        operation.totalTime = args.totalTime instanceof Amount ? args.totalTime : Duration.millis(args?.totalTime ?: 120)
        operation.exception = args?.failure
        return operation
    }

    BuildDisplayInfo buildDisplayInfo(String displayName) {
        return new BuildDisplayInfo('test-project', displayName, [], [], [], [], false)
    }

    PerformanceTestHistory mockCrossVersionHistory() {
        CrossVersionPerformanceResults result1 = crossVersionResults([startTime: 100])
        result1.version('5.0-mockbaseline-1').results.addAll(measuredOperations([1]))
        result1.version('master').results.addAll(measuredOperations([2]))

        CrossVersionPerformanceResults result2 = crossVersionResults([startTime: 200])
        result2.version('5.0-mockbaseline-2').results.addAll(measuredOperations([2, 2]))
        result2.version('master').results.addAll(measuredOperations([1, 1]))

        return new CrossVersionPerformanceTestHistory(new PerformanceExperiment('test-project', new PerformanceScenario('org.gradle.performance.MyPerformanceTest', 'mockScenario')),
            ['5.0-mockbaseline-1', '5.0-mockbaseline-2'],
            ['master'],
            [result2, result1]
        )
    }

    PerformanceTestHistory mockCrossBuildHistory() {
        BuildDisplayInfo info1 = buildDisplayInfo('build1')
        CrossBuildPerformanceResults result1  = crossBuildResults(startTime: 100)
        result1.buildResult(info1).addAll(measuredOperations([1]))

        BuildDisplayInfo info2 = buildDisplayInfo('build2')
        CrossBuildPerformanceResults result2  = crossBuildResults(startTime: 200)
        result1.buildResult(info2).addAll(measuredOperations([2]))

        return new CrossBuildPerformanceTestHistory(new PerformanceExperiment("test-project", new PerformanceScenario("org.gradle.performance.MyCrossBuildPerformanceTest", "my scenario name")), [info1, info2], [result2, result1])
    }

    List<MeasuredOperation> measuredOperations(List<Integer> values) {
        return values.collect { new MeasuredOperation(totalTime: Amount.valueOf(it, Duration.SECONDS)) }
    }

    MeasuredOperationList measuredOperationList(List<Integer> values) {
        MeasuredOperationList ret = new MeasuredOperationList()
        ret.addAll(measuredOperations(values))
        return ret
    }
}
