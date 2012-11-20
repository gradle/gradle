/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.peformance.fixture

import spock.lang.Specification

class PerformanceResultsTest extends Specification {
    def PerformanceResults result = new PerformanceResults()

    def "passes when average execution time for current release is smaller than average execution time for previous release"() {
        given:
        result.baselineVersions[0].results.add(operation(executionTime: 110))
        result.baselineVersions[0].results.add(operation(executionTime: 100))
        result.baselineVersions[0].results.add(operation(executionTime: 90))

        and:
        result.current.add(operation(executionTime: 90))
        result.current.add(operation(executionTime: 110))
        result.current.add(operation(executionTime: 90))

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "passes when average execution time for current release is within specified range of average execution time for previous release"() {
        given:
        result.baselineVersions[0].maxExecutionTimeRegression = Duration.millis(10)
        result.baselineVersions[0].results.add(operation(executionTime: 100))
        result.baselineVersions[0].results.add(operation(executionTime: 100))
        result.baselineVersions[0].results.add(operation(executionTime: 100))

        and:
        result.current.add(operation(executionTime: 110))
        result.current.add(operation(executionTime: 110))
        result.current.add(operation(executionTime: 110))

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when average execution time for current release is larger than average execution time for previous release"() {
        given:
        result.displayName = '<test>'
        result.baselineVersions[0].maxExecutionTimeRegression = Duration.millis(10)
        result.baselineVersions[0].results.add(operation(executionTime: 100))
        result.baselineVersions[0].results.add(operation(executionTime: 100))
        result.baselineVersions[0].results.add(operation(executionTime: 100))

        and:
        result.current.add(operation(executionTime: 110))
        result.current.add(operation(executionTime: 110))
        result.current.add(operation(executionTime: 111))

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Speed <test>: we're slower than 1.x.")
        e.message.contains('Difference: 10.333 ms slower (10.333 ms), 10.33%')
    }

    def "passes when average heap usage for current release is smaller than average heap usage for previous release"() {
        given:
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))

        and:
        result.current.add(operation(heapUsed: 1000))
        result.current.add(operation(heapUsed: 1005))
        result.current.add(operation(heapUsed: 994))

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "passes when average heap usage for current release is slightly larger than average heap usage for previous release"() {
        given:
        result.baselineVersions[0].maxMemoryRegression = DataAmount.bytes(100)
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))

        and:
        result.current.add(operation(heapUsed: 1100))
        result.current.add(operation(heapUsed: 1100))
        result.current.add(operation(heapUsed: 1100))

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when average heap usage for current release is larger than average heap usage for previous release"() {
        given:
        result.displayName = '<test>'
        result.baselineVersions[0].maxMemoryRegression = DataAmount.bytes(100)
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000))

        and:
        result.current.add(operation(heapUsed: 1100))
        result.current.add(operation(heapUsed: 1100))
        result.current.add(operation(heapUsed: 1101))

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith('Memory <test>: we need more memory than 1.x.')
        e.message.contains('Difference: 100.333 B more (100.333 B), 10.03%')
    }

    def "fails when both heap usage and execution time have regressed"() {
        given:
        result.displayName = '<test>'
        result.baselineVersions[0].results.add(operation(heapUsed: 1000, executionTime: 100))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000, executionTime: 100))
        result.baselineVersions[0].results.add(operation(heapUsed: 1000, executionTime: 100))

        and:
        result.current.add(operation(heapUsed: 1100, executionTime: 110))
        result.current.add(operation(heapUsed: 1100, executionTime: 110))
        result.current.add(operation(heapUsed: 1101, executionTime: 111))

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.contains("Speed <test>: we're slower than 1.x.")
        e.message.contains('Difference: 10.333 ms slower (10.333 ms)')
        e.message.contains('Memory <test>: we need more memory than 1.x.')
        e.message.contains('Difference: 100.333 B more (100.333 B)')
    }

    def "fails when a previous operation fails"() {
        given:
        result.baselineVersions[0].results.add(operation(failure: new RuntimeException()))
        result.current.add(operation())

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Some builds have failed.")
    }

    def "fails when a current operation fails"() {
        given:
        result.baselineVersions[0].results.add(operation())
        result.current.add(operation(failure: new RuntimeException()))

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Some builds have failed.")
    }

    def "fails when an operation fails"() {
        given:
        result.current.add(operation())
        result.baselineVersions << new BaselineVersion(version: 'oldVersion', results: new MeasuredOperationList())
        result.baselineVersions[0].results.add(operation())
        result.baselineVersions[1].results.add(operation(failure: new RuntimeException()))

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Some builds have failed.")
    }

    //TODO SF add more coverage for multiple baseline versions

    private MeasuredOperation operation(Map<String, Object> args) {
        def operation = new MeasuredOperation()
        operation.executionTime = Duration.millis(args?.executionTime ?: 120)
        operation.totalMemoryUsed = DataAmount.bytes(args?.heapUsed ?: 1024)
        operation.exception = args?.failure
        return operation
    }

}
