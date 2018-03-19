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
    def "normalizes line ending in output"() {
        def output = "\n\r\nabc\r\n\n"
        def error = "\r\n\nerror\n\r\n"
        def result = OutputScrapingExecutionResult.from(output, error)

        expect:
        result.output == "\n\nabc\n\n"
        result.error == "\n\nerror\n\n"
    }

    def "retains trailing line ending in output"() {
        def output = "\n\nabc\n"
        def error = "\nerror\n"
        def result = OutputScrapingExecutionResult.from(output, error)

        expect:
        result.output == output
        result.error == error
    }

    def "can assert build output is present in main content"() {
        def output = """
message

BUILD SUCCESSFUL in 12s

post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

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
        def result = OutputScrapingExecutionResult.from(output, "")

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

    def "can assert task is present when task output is split into several groups"() {
        def output = """
before

> Task :a
> Task :b

> Task :a
some content

> Task :b
other content

> Task :a
all good

> Task :b SKIPPED

after

BUILD SUCCESSFUL in 12s

post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertTasksExecuted(":a", ":b")
        result.assertTasksExecuted([":a", ":b"])
        result.assertTasksExecuted(":a", ":b", ":a", [":a", ":b"], ":b")

        and:
        result.assertTasksExecutedInOrder(":a", ":b")
        result.assertTasksExecutedInOrder([":a", ":b"])

        and:
        result.executedTasks == [":a", ":b"]
        result.skippedTasks == [":b"] as Set

        and:
        result.assertTasksNotSkipped(":a")
        result.assertTasksNotSkipped(":a", ":a", [":a"])

        and:
        result.assertTaskNotSkipped(":a")

        and:
        result.assertTasksSkipped(":b")
        result.assertTasksSkipped(":b", ":b", [":b"])

        and:
        result.assertTaskSkipped(":b")

        when:
        result.assertTasksExecuted(":a")

        then:
        def e = thrown(AssertionError)
        e.message.startsWith('''
            Build output does not contain the expected tasks.
            Expected: [:a]
            Actual: [:a, :b]
        '''.stripIndent().trim())

        when:
        result.assertTasksExecuted(":a", ":b", ":c")

        then:
        def e2 = thrown(AssertionError)
        e2.message.startsWith('''
            Build output does not contain the expected tasks.
            Expected: [:a, :b, :c]
            Actual: [:a, :b]
        '''.stripIndent().trim())

        when:
        result.assertTasksExecutedInOrder(":b", ":a")

        then:
        def e3 = thrown(AssertionError)
        e3.message.startsWith(":a does not occur in expected order (expected: exact([:b, :a]), actual [:a, :b])")

        when:
        result.assertTasksExecutedInOrder(":a")

        then:
        def e4 = thrown(AssertionError)
        e4.message.startsWith('''
            Build output does not contain the expected tasks.
            Expected: [:a]
            Actual: [:a, :b]
        '''.stripIndent().trim())

        when:
        result.assertTasksExecutedInOrder(":a", ":b", ":c")

        then:
        def e5 = thrown(AssertionError)
        e5.message.startsWith('''
            Build output does not contain the expected tasks.
            Expected: [:a, :b, :c]
            Actual: [:a, :b]
        '''.stripIndent().trim())

        when:
        result.assertTasksSkipped()

        then:
        def e6 = thrown(AssertionError)
        e6.message.startsWith('''
            Build output does not contain the expected skipped tasks.
            Expected: []
            Actual: [:b]
        '''.stripIndent().trim())

        when:
        result.assertTasksSkipped(":a")

        then:
        def e7 = thrown(AssertionError)
        e7.message.startsWith('''
            Build output does not contain the expected skipped tasks.
            Expected: [:a]
            Actual: [:b]
        '''.stripIndent().trim())

        when:
        result.assertTasksSkipped(":b", ":c")

        then:
        def e8 = thrown(AssertionError)
        e8.message.startsWith('''
            Build output does not contain the expected skipped tasks.
            Expected: [:b, :c]
            Actual: [:b]
        '''.stripIndent().trim())

        when:
        result.assertTaskSkipped(":a")

        then:
        def e9 = thrown(AssertionError)
        e9.message.startsWith('''
            Build output does not contain the expected skipped task.
            Expected: :a
            Actual: [:b]
        '''.stripIndent().trim())

        when:
        result.assertTasksNotSkipped()

        then:
        def e10 = thrown(AssertionError)
        e10.message.startsWith('''
            Build output does not contain the expected non skipped tasks.
            Expected: []
            Actual: [:a]
        '''.stripIndent().trim())

        when:
        result.assertTasksNotSkipped(":b")

        then:
        def e11 = thrown(AssertionError)
        e11.message.startsWith('''
            Build output does not contain the expected non skipped tasks.
            Expected: [:b]
            Actual: [:a]
        '''.stripIndent().trim())

        when:
        result.assertTasksNotSkipped(":a", ":c")

        then:
        def e12 = thrown(AssertionError)
        e12.message.startsWith('''
            Build output does not contain the expected non skipped tasks.
            Expected: [:a, :c]
            Actual: [:a]
        '''.stripIndent().trim())

        when:
        result.assertTaskNotSkipped(":b")

        then:
        def e13 = thrown(AssertionError)
        e13.message.startsWith('''
            Build output does not contain the expected non skipped task.
            Expected: :b
            Actual: [:a]
        '''.stripIndent().trim())
    }

    def "creates failure result"() {
        def output = """
message

BUILD FAILED in 12s

post build
"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result instanceof OutputScrapingExecutionFailure
    }

    def "can capture tasks with multiple headers"() {
        def output = """
> Task :compileMyTestBinaryMyTestJava
> Task :myTestBinaryTest

MyTest > test FAILED
    java.lang.AssertionError at MyTest.java:10

1 test completed, 1 failed

> Task :myTestBinaryTest FAILED


FAILURE: Build failed with an exception.

BUILD FAILED in 13s
2 actionable tasks: 2 executed

"""
        when:
        def result = OutputScrapingExecutionResult.from(output, "")

        then:
        result.assertTasksExecutedInOrder(":compileMyTestBinaryMyTestJava", ":myTestBinaryTest")
    }

    def error(String text) {
        return TextUtil.normaliseLineSeparators(text.stripIndent().trim())
    }
}
