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

package org.gradle.performance.fixture

import org.gradle.performance.ResultSpecification
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration

class CrossVersionPerformanceResultsTest extends ResultSpecification {
    def CrossVersionPerformanceResults result = new CrossVersionPerformanceResults(testProject: "some-project", tasks: [])

    def "passes when average execution time for current release is smaller than average execution time for previous releases"() {
        given:
        result.baseline("1.0").results.add(operation(totalTime: 110))
        result.baseline("1.0").results.add(operation(totalTime: 100))
        result.baseline("1.0").results.add(operation(totalTime: 90))

        and:
        result.current.add(operation(totalTime: 90))
        result.current.add(operation(totalTime: 110))
        result.current.add(operation(totalTime: 90))

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "passes when average execution time for current release is within specified range of average execution time for previous releases"() {
        given:
        result.baseline("1.0").maxExecutionTimeRegression = Duration.millis(10)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)

        result.baseline("1.3").results << operation(totalTime: 115)
        result.baseline("1.3").results << operation(totalTime: 105)
        result.baseline("1.3").results << operation(totalTime: 110)

        and:
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 110)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when average execution time for current release is larger than average execution time for previous releases"() {
        given:
        result.baseline("1.0").maxExecutionTimeRegression = Duration.millis(10)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)

        result.baseline("1.3").maxExecutionTimeRegression = Duration.millis(10)
        result.baseline("1.3").results << operation(totalTime: 101)
        result.baseline("1.3").results << operation(totalTime: 100)
        result.baseline("1.3").results << operation(totalTime: 100)

        and:
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 111)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Speed ${result.displayName}: we're slower than 1.0.")
        e.message.contains('Difference: 10.333 ms slower (10.333 ms), 10.33%')
        !e.message.contains('1.3')
    }

    def "passes when average heap usage for current release is smaller than average heap usage for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)

        result.baseline("1.3").results << operation(totalMemoryUsed: 800)
        result.baseline("1.3").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.3").results << operation(totalMemoryUsed: 1200)

        and:
        result.current << operation(totalMemoryUsed: 1000)
        result.current << operation(totalMemoryUsed: 1005)
        result.current << operation(totalMemoryUsed: 994)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "passes when average heap usage for current release is slightly larger than average heap usage for previous releases"() {
        given:
        result.baseline("1.0").maxMemoryRegression = DataAmount.bytes(100)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)

        result.baseline("1.3").maxMemoryRegression = DataAmount.bytes(100)
        result.baseline("1.3").results << operation(totalMemoryUsed: 900)
        result.baseline("1.3").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.3").results << operation(totalMemoryUsed: 1100)

        and:
        result.current << operation(totalMemoryUsed: 1100)
        result.current << operation(totalMemoryUsed: 1100)
        result.current << operation(totalMemoryUsed: 1100)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when average heap usage for current release is larger than average heap usage for previous releases"() {
        given:
        result.baseline("1.0").maxMemoryRegression = DataAmount.bytes(100)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1001)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1001)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1001)

        result.baseline("1.2").maxMemoryRegression = DataAmount.bytes(100)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000)

        and:
        result.current << operation(totalMemoryUsed: 1100)
        result.current << operation(totalMemoryUsed: 1100)
        result.current << operation(totalMemoryUsed: 1101)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Memory ${result.displayName}: we need more memory than 1.2.")
        e.message.contains('Difference: 100.333 B more (100.333 B), 10.03%')
        !e.message.contains('than 1.0')
    }

    def "fails when both heap usage and execution time have regressed"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 150)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 150)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 100)

        and:
        result.current << operation(totalMemoryUsed: 1100, totalTime: 110)
        result.current << operation(totalMemoryUsed: 1100, totalTime: 110)
        result.current << operation(totalMemoryUsed: 1101, totalTime: 111)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.contains("Speed ${result.displayName}: we're slower than 1.2.")
        e.message.contains('Difference: 10.333 ms slower (10.333 ms)')
        e.message.contains("Memory ${result.displayName}: we need more memory than 1.2.")
        e.message.contains('Difference: 100.333 B more (100.333 B)')
        !e.message.contains('than 1.0')
    }

    def "fails when a previous operation fails"() {
        given:
        result.baseline("1.0").results << operation(failure: new RuntimeException())
        result.current.add(operation())

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Some builds have failed.")
    }

    def "fails when a current operation fails"() {
        given:
        result.baseline("1.0").results << operation()
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
        result.baseline("1.0").results << operation()
        result.baseline("oldVersion").results << operation(failure: new RuntimeException())

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Some builds have failed.")
    }

    def "fails if one of the baseline version is faster and the other needs less memory"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 100)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 100)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 150)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 150)

        and:
        result.current << operation(totalMemoryUsed: 1100, totalTime: 125)
        result.current << operation(totalMemoryUsed: 1100, totalTime: 125)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.contains("Speed ${result.displayName}: we're slower than 1.0.")
        e.message.contains('Difference: 25 ms slower')
        e.message.contains("Memory ${result.displayName}: we need more memory than 1.2.")
        e.message.contains('Difference: 100 B more')
        e.message.count(result.displayName) == 2
    }

    def "fails if all of the baseline versions are better in every respect"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 120)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 120)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1100, totalTime: 150)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1100, totalTime: 150)

        and:
        result.current << operation(totalMemoryUsed: 1300, totalTime: 200)
        result.current << operation(totalMemoryUsed: 1300, totalTime: 200)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.contains("Speed ${result.displayName}: we're slower than 1.0.")
        e.message.contains("Speed ${result.displayName}: we're slower than 1.2.")
        e.message.contains("Memory ${result.displayName}: we need more memory than 1.0.")
        e.message.contains("Memory ${result.displayName}: we need more memory than 1.2.")
    }

    def "can lookup the results for a baseline version"() {
        expect:
        def baseline = result.baseline("1.0")
        baseline.version == "1.0"

        and:
        result.baseline("1.0") == baseline
        result.version("1.0") == baseline
    }

    def "can lookup the current results using the branch as the version name"() {
        given:
        result.vcsBranch = 'master'

        expect:
        def version = result.version('master')
        version.results.is(result.current)
    }
}
