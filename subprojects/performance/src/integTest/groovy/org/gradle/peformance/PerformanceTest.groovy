/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.peformance

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.ReleasedVersions
import org.gradle.peformance.fixture.PerformanceTestRunner
import spock.lang.Specification
import spock.lang.Unroll

/**
 * by Szczepan Faber, created at: 2/9/12
 */
class PerformanceTest extends Specification {

    def current = new GradleDistribution()
    def previous = new ReleasedVersions(current).last

    @Unroll("Project '#testProject' ran #runs times. Current release is not slower than the previous one.")
    def "speed"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject, runs: runs, warmUpRuns: 1, accuracyMs: accuracyMs).run()
        result.assertCurrentReleaseIsNotSlower()

        where:
        testProject | runs | accuracyMs
        "small"     | 10   | 500
        "multi"     | 10   | 1000
    }

    @Unroll("Project '#testProject' with heap size: #heapSize. Current release does not require more memory than the previous one.")
    def "memory"() {
        expect:
        def result = new PerformanceTestRunner(testProject: testProject, runs: 1, gradleOpts: [heapSize]).run()
        result.assertEveryBuildSucceeds()

        where:
        testProject | heapSize
        "small"     | '-Xmx19m' //fails with 16m
        "multi"     | '-Xmx66m' //fails with 54m on my box, with 60m on ci
    }
}