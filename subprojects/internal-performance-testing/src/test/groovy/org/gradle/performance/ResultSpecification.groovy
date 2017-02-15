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
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.GradleVsMavenBuildPerformanceResults
import org.joda.time.DateTime
import spock.lang.Specification

abstract class ResultSpecification extends Specification {
    final String channel = 'commits'

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
        results.channel = channel
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
                startTime: 100,
                vcsBranch: "master",
                vcsCommits: ["abcdef"],
                channel: channel
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
        operation.start = args.start instanceof DateTime ? args.start : new DateTime(args?.start ?: 0)
        operation.end = args.end instanceof DateTime ? args.end : new DateTime(args?.end ?: 0)
        operation.exception = args?.failure
        return operation
    }
}
