/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import spock.lang.Specification


class OutputScrapingExecutionResultTest extends Specification {
    def "normalizes line ending"() {
        def output = "\n\r\nabc\r\n\n"
        def error = "\r\n\nerror\n\r\n"
        def result = new OutputScrapingExecutionResult(output, error)

        expect:
        result.output == "\n\nabc\n\n"
        result.error == "\n\nerror\n\n"
    }

    def "retains trailing line ending"() {
        def output = "\n\nabc\n"
        def error = "\nerror\n"
        def result = new OutputScrapingExecutionResult(output, error)

        expect:
        result.output == output
        result.error == error
    }

    def "can assert build output is present"() {
        def output = """
message

BUILD SUCCESSFUL in 12s

post build
"""
        when:
        def result = new OutputScrapingExecutionResult(output, "")

        then:
        result.assertOutputContains("message")

        when:
        result.assertOutputContains("missing")

        then:
        def e = thrown(AssertionError)
        e.message.startsWith('Substring not found in build output')

        when:
        result.assertOutputContains("post build")

        then:
        def e2 = thrown(AssertionError)
        e2.message.startsWith('Substring not found in build output')

        when:
        result.assertOutputContains("BUILD")

        then:
        def e3 = thrown(AssertionError)
        e3.message.startsWith('Substring not found in build output')
    }

    def "can assert post build output is present"() {
        def output = """
message

BUILD SUCCESSFUL in 12s

post build
"""
        when:
        def result = new OutputScrapingExecutionResult(output, "")

        then:
        result.assertHasPostBuildOutput("post build")

        when:
        result.assertHasPostBuildOutput("missing")

        then:
        def e = thrown(AssertionError)
        e.message.startsWith('Substring not found in build output')

        when:
        result.assertHasPostBuildOutput("message")

        then:
        def e2 = thrown(AssertionError)
        e2.message.startsWith('Substring not found in build output')

        when:
        result.assertHasPostBuildOutput("BUILD")

        then:
        def e3 = thrown(AssertionError)
        e3.message.startsWith('Substring not found in build output')
    }
}
