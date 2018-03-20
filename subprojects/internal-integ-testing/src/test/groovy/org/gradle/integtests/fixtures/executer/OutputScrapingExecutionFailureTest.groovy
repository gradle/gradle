/*
 * Copyright 2017 the original author or authors.
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

class OutputScrapingExecutionFailureTest extends AbstractExecutionResultTest {
    def "can have empty output"() {
        def result = OutputScrapingExecutionFailure.from("", "")

        expect:
        result.output.empty
        result.normalizedOutput.empty
        result.error.empty
    }

    def "can assert that failure location is present"() {
        given:
        def output = """
FAILURE: broken

* Where: build file 'build.gradle' line: 123

* What went wrong: something bad
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertHasFileName("build file 'build.gradle'")
        failure.assertHasLineNumber(123)

        when:
        failure.assertHasFileName("none")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: "none"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: "23"')
    }

    def "cannot assert that failure location is present when missing"() {
        given:
        def output = """
FAILURE: broken

* What went wrong: something bad
"""
        def failure = OutputScrapingExecutionFailure.from(output, "")

        when:
        failure.assertHasFileName("build.gradle")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: "build.gradle"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: "23"')
    }

    def "cannot make assertions about failures when failure section is missing"() {
        given:
        def output = """
some message.

broken!
"""
        def failure = OutputScrapingExecutionFailure.from(output, "")

        when:
        failure.assertHasDescription("broken!")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: a string starting with "broken!"')

        when:
        failure.assertHasFileName("build.gradle")

        then:
        def e2 = thrown(AssertionError)
        e2.message.trim().startsWith('Expected: "build.gradle"')

        when:
        failure.assertHasLineNumber(23)

        then:
        def e3 = thrown(AssertionError)
        e3.message.trim().startsWith('Expected: "23"')
    }

    def "log output present assertions ignore content after failure section"() {
        given:
        def output = """
Some message
Some error

FAILURE: broken

* Exception is:
Some.Failure
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertOutputContains("Some message")
        failure.assertHasErrorOutput("Some error")

        when:
        failure.assertOutputContains("broken")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Did not find expected text in build output.
            Expected: broken
             
            Build output:
            =======
             
            Some message
            Some error
             
            Output:
        '''))

        when:
        failure.assertHasErrorOutput("broken")

        then:
        def e2 = thrown(AssertionError)
        error(e2).startsWith(error('''
            Did not find expected text in build output.
            Expected: broken
             
            Build output:
            =======
             
            Some message
            Some error
             
            Output:
        '''))
    }

    def "log output missing assertions do not ignore content after failure section"() {
        given:
        def output = """
Some message
Some error

FAILURE: broken

* Exception is:
Some.Failure
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.assertNotOutput("missing")

        when:
        failure.assertNotOutput("broken")

        then:
        def e = thrown(AssertionError)
        error(e).startsWith(error('''
            Found unexpected text in build output.
            Expected not present: broken
             
            Output:
        '''))
    }

    def "recreates exception stack trace"() {
        given:
        def output = """
Some text before

FAILURE: broken

* Exception is:
org.gradle.internal.service.ServiceCreationException: Could not create service of type CacheLockingManager
    at org.gradle.internal.service.DefaultServiceRegistry.some(DefaultServiceRegistry.java:604)
Caused by: java.io.IOException: Something in the middle
    at org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager.initMetaDataStoreDir(DefaultCacheLockingManager.java:59)
Caused by: org.gradle.api.UncheckedIOException: Unable to create directory 'metadata-2.1'
    at org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager.initMetaDataStoreDir(DefaultCacheLockingManager.java:59)
"""
        when:
        def failure = OutputScrapingExecutionFailure.from(output, "")

        then:
        failure.exception.class.simpleName == 'ServiceCreationException'
        failure.exception.message == 'Could not create service of type CacheLockingManager'
        failure.exception.cause.class.simpleName == 'IOException'
        failure.exception.cause.message == 'Something in the middle'
        failure.exception.cause.cause.class.simpleName == 'UncheckedIOException'
        failure.exception.cause.cause.message == "Unable to create directory 'metadata-2.1'"
    }

}
