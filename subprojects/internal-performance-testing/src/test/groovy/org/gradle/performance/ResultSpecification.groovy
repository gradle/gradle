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

import org.gradle.performance.fixture.CrossBuildPerformanceResults
import org.gradle.performance.fixture.CrossVersionPerformanceResults
import org.gradle.performance.fixture.GradleVsMavenBuildPerformanceResults
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import spock.lang.Specification

abstract class ResultSpecification extends Specification {
    CrossVersionPerformanceResults crossVersionResults(Map<String, ?> options = [:]) {
        def results = new CrossVersionPerformanceResults()
        results.testId = "test-id"
        results.previousTestIds = []
        results.testProject = "test-project"
        results.tasks = ["clean", "build"]
        results.args = []
        results.gradleOpts = []
        results.daemon = false
        results.operatingSystem = "some os"
        results.jvm = "java 6"
        results.versionUnderTest = "1.7-rc-1"
        results.vcsBranch = "master"
        results.vcsCommits = ['123456']
        options.each { key, value -> results."$key" = value }
        return results
    }

    CrossBuildPerformanceResults crossBuildResults(Map<String, ?> options = [:]) {
        def results = new CrossBuildPerformanceResults(
                testId: "test-id",
                testGroup: "test-group",
                jvm: "java 7",
                versionUnderTest: "Gradle 1.0",
                operatingSystem: "windows",
                testTime: 100,
                vcsBranch: "master",
                vcsCommits: ["abcdef"]
        )
        options.each { key, value -> results."$key" = value }
        return results
    }

    GradleVsMavenBuildPerformanceResults gradleVsMavenBuildResults(Map<String, ?> options = [:]) {
        def results = new GradleVsMavenBuildPerformanceResults(
                testId: "test-id",
                testGroup: "test-group",
                jvm: "java 7",
                versionUnderTest: "Gradle 1.0",
                operatingSystem: "windows",
                testTime: 100,
                vcsBranch: "master",
                vcsCommits: ["abcdef"]
        )
        options.each { key, value -> results."$key" = value }
        return results
    }

    MeasuredOperation operation(Map<String, Object> args = [:]) {
        def operation = new MeasuredOperation()
        operation.totalTime = args.totalTime instanceof Amount ? args.totalTime : Duration.millis(args?.totalTime ?: 120)
        operation.configurationTime = args.configurationTime instanceof Amount ? args.configurationTime : Duration.millis(args.configurationTime ?: 30);
        operation.executionTime = args.executionTime instanceof Amount ? args.executionTime : Duration.millis(args.configurationTime ?: 80);
        operation.totalMemoryUsed = args.totalMemoryUsed instanceof Amount ? args.totalMemoryUsed : DataAmount.bytes(args?.totalMemoryUsed ?: 1024)
        operation.totalHeapUsage = args.totalHeapUsage instanceof Amount ? args.totalHeapUsage : DataAmount.bytes(args?.totalHeapUsage ?: 4096)
        operation.maxHeapUsage = args.maxHeapUsage instanceof Amount ? args.maxHeapUsage : DataAmount.bytes(args?.maxHeapUsage ?: 2000)
        operation.maxUncollectedHeap = args.maxUncollectedHeap instanceof Amount ? args.maxUncollectedHeap : DataAmount.bytes(args?.maxUncollectedHeap ?: 120)
        operation.maxCommittedHeap = args.maxCommittedHeap instanceof Amount ? args.maxCommittedHeap : DataAmount.bytes(args?.maxCommittedHeap ?: 3000)
        operation.exception = args?.failure
        return operation
    }
}
