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

class CrossVersionPerformanceTestExecutionTest extends ResultSpecification {
    def result = new CrossVersionPerformanceResults(testProject: "some-project", tasks: [], cleanTasks: [])

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

    def "passes when average execution time for current release is within allowed range of average execution time for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)

        result.baseline("1.3").results << operation(totalTime: 115)
        result.baseline("1.3").results << operation(totalTime: 105)
        result.baseline("1.3").results << operation(totalTime: 110)

        and:
        result.current << operation(totalTime: 100)
        result.current << operation(totalTime: 100)
        result.current << operation(totalTime: 100)

        expect:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "fails when median execution time for current release is larger than median execution time for previous releases"() {
        given:
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)
        result.baseline("1.0").results << operation(totalTime: 100)

        result.baseline("1.3").results << operation(totalTime: 110)
        result.baseline("1.3").results << operation(totalTime: 110)
        result.baseline("1.3").results << operation(totalTime: 111)
        result.baseline("1.3").results << operation(totalTime: 111)
        result.baseline("1.3").results << operation(totalTime: 111)
        result.baseline("1.3").results << operation(totalTime: 110)
        result.baseline("1.3").results << operation(totalTime: 110)
        result.baseline("1.3").results << operation(totalTime: 111)
        result.baseline("1.3").results << operation(totalTime: 111)
        result.baseline("1.3").results << operation(totalTime: 111)

        and:
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 111)
        result.current << operation(totalTime: 111)
        result.current << operation(totalTime: 111)
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 110)
        result.current << operation(totalTime: 111)
        result.current << operation(totalTime: 111)
        result.current << operation(totalTime: 111)

        when:
        result.assertCurrentVersionHasNotRegressed()

        then:
        AssertionError e = thrown()
        e.message.startsWith("Speed ${result.displayName}: we're slower than 1.0 with 99% confidence.")
        e.message.contains('Difference: 11 ms slower (11 ms), 11.00%')
        !e.message.contains('1.3')
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
