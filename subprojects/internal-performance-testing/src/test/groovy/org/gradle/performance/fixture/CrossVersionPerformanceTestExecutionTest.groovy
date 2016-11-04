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
import org.gradle.performance.results.CrossVersionPerformanceResults
import org.gradle.performance.results.MeasuredOperationList

class CrossVersionPerformanceTestExecutionTest extends ResultSpecification {
    def CrossVersionPerformanceResults result = new CrossVersionPerformanceResults(testProject: "some-project", tasks: [])

    def "passes when average execution time for current release is smaller than average execution time for previous releases"() {
        given:
        result.baseline("1.0").results.add(operation(totalTime: 110))
        result.baseline("1.0").results.add(operation(totalTime: 100))
        result.baseline("1.0").results.add(operation(totalTime: 90))
        addMinAndMax(result.baseline("1.0").results)

        and:
        result.current.add(operation(totalTime: 90))
        result.current.add(operation(totalTime: 110))
        result.current.add(operation(totalTime: 90))
        addMinAndMax(result.current)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    // min and max values are ignored in calculations
    def addMinAndMax(MeasuredOperationList results) {
        results.add(operation(totalTime: 1, totalMemoryUsed: 1))
        results.add(operation(totalTime: 999, totalMemoryUsed: 9999))
    }

    def "passes when average execution time for current release is within allowed range of average execution time for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.3").results << operation(totalTime: 115)
        result.baseline("1.3").results << operation(totalTime: 105)
        result.baseline("1.3").results << operation(totalTime: 110)
        addMinAndMax(result.baseline("1.3").results)

        and:
        result.current << operation(totalTime: 100)
        result.current << operation(totalTime: 100)
        result.current << operation(totalTime: 100)
        addMinAndMax(result.current)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when average execution time for current release is larger than average execution time for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.3").results << operation(totalTime: 110)
        result.baseline("1.3").results << operation(totalTime: 110)
        result.baseline("1.3").results << operation(totalTime: 111)
        addMinAndMax(result.baseline("1.3").results)

        and:
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 111)
        addMinAndMax(result.current)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Speed ${result.displayName}: we're slower than 1.0.")
        e.message.contains('Difference: 6.2 ms slower (6.2 ms), 2.38%')
        !e.message.contains('1.3')
    }

    def "passes when average heap usage for current release is smaller than average heap usage for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000)
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.3").results << operation(totalMemoryUsed: 800)
        result.baseline("1.3").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.3").results << operation(totalMemoryUsed: 1200)
        addMinAndMax(result.baseline("1.3").results)

        and:
        result.current << operation(totalMemoryUsed: 1000)
        result.current << operation(totalMemoryUsed: 1005)
        result.current << operation(totalMemoryUsed: 994)
        addMinAndMax(result.current)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when average heap usage for current release is larger than average heap usage for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1001)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1001)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1001)
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000)
        addMinAndMax(result.baseline("1.2").results)

        and:
        result.current << operation(totalMemoryUsed: 1000)
        result.current << operation(totalMemoryUsed: 1001)
        result.current << operation(totalMemoryUsed: 1001)
        addMinAndMax(result.current)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Memory ${result.displayName}: we need more memory than 1.2.")
        e.message.contains('Difference: 0.4 B more (0.4 B), 0.02%')
        !e.message.contains('than 1.0')
    }

    def "fails when both heap usage and execution time have regressed"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 150)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 150)
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 100)
        addMinAndMax(result.baseline("1.2").results)

        and:
        result.current << operation(totalMemoryUsed: 1100, totalTime: 110)
        result.current << operation(totalMemoryUsed: 1100, totalTime: 110)
        result.current << operation(totalMemoryUsed: 1101, totalTime: 111)
        addMinAndMax(result.current)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.contains("Speed ${result.displayName}: we're slower than 1.2.")
        e.message.contains('Difference: 6.2 ms slower (6.2 ms)')
        e.message.contains("Memory ${result.displayName}: we need more memory than 1.2.")
        e.message.contains('Difference: 60.2 B more (60.2 B)')
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
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 150)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1000, totalTime: 150)
        addMinAndMax(result.baseline("1.2").results)

        and:
        result.current << operation(totalMemoryUsed: 1100, totalTime: 125)
        result.current << operation(totalMemoryUsed: 1100, totalTime: 125)
        addMinAndMax(result.current)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.contains("Speed ${result.displayName}: we're slower than 1.0.")
        e.message.contains('Difference: 12.5 ms slower')
        e.message.contains("Memory ${result.displayName}: we need more memory than 1.2.")
        e.message.contains('Difference: 50 B more')
        e.message.count(result.displayName) == 2
    }

    def "fails if all of the baseline versions are better in every respect"() {
        given:
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 120)
        result.baseline("1.0").results << operation(totalMemoryUsed: 1200, totalTime: 120)
        addMinAndMax(result.baseline("1.0").results)

        result.baseline("1.2").results << operation(totalMemoryUsed: 1100, totalTime: 150)
        result.baseline("1.2").results << operation(totalMemoryUsed: 1100, totalTime: 150)
        addMinAndMax(result.baseline("1.2").results)

        and:
        result.current << operation(totalMemoryUsed: 1300, totalTime: 200)
        result.current << operation(totalMemoryUsed: 1300, totalTime: 200)
        addMinAndMax(result.current)

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
