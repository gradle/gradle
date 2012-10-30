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
        result.addResult(operation(executionTime: 110), operation(executionTime: 90))
        result.addResult(operation(executionTime: 100), operation(executionTime: 110))
        result.addResult(operation(executionTime: 90), operation(executionTime: 90))

        expect:
        result.assertCurrentReleaseIsNotSlower()
    }

    def "passes when average execution time for current release is within specified range of average execution time for previous release"() {
        given:
        result.accuracyMs = 10
        result.addResult(operation(executionTime: 100), operation(executionTime: 110))
        result.addResult(operation(executionTime: 100), operation(executionTime: 110))
        result.addResult(operation(executionTime: 100), operation(executionTime: 110))

        expect:
        result.assertCurrentReleaseIsNotSlower()
    }

    def "fails when average execution time for current release is larger than average execution time for previous release"() {
        given:
        result.accuracyMs = 10
        result.addResult(operation(executionTime: 100), operation(executionTime: 110))
        result.addResult(operation(executionTime: 100), operation(executionTime: 110))
        result.addResult(operation(executionTime: 100), operation(executionTime: 111))

        when:
        result.assertCurrentReleaseIsNotSlower()

        then:
        AssertionError e = thrown()
        e.message.startsWith('Looks like the current gradle is slower than latest release.')
        e.message.contains('Difference: 0.01 secs (10.33 ms)')
    }

    def "passes when average heap usage for current release is smaller than average heap usage for previous release"() {
        given:
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1000))
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1005))
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 994))

        expect:
        result.assertMemoryUsed(0)
    }

    def "passes when average heap usage for current release is slightly larger than average heap usage for previous release"() {
        given:
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1100))
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1100))
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1100))

        expect:
        result.assertMemoryUsed(0.1)
    }

    def "fails when average heap usage for current release is larger than average heap usage for previous release"() {
        given:
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1100))
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1100))
        result.addResult(operation(heapUsed: 1000), operation(heapUsed: 1101))

        when:
        result.assertMemoryUsed(0.1)

        then:
        AssertionError e = thrown()
        e.message.startsWith('Looks like the current gradle requires more memory than the latest release.')
        e.message.contains('Difference: 100 B (100.33 B)')
    }

    private MeasuredOperation operation(Map<String, Object> args) {
        def operation = new MeasuredOperation()
        operation.executionTime = args.executionTime ?: 120
        operation.prettyTime = operation.executionTime
        operation.totalMemoryUsed = args.heapUsed ?: 1024
        return operation
    }

}
