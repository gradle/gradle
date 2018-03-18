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

import spock.lang.Specification

class OutputScrapingExecutionFailureTest extends Specification {
    def "cannot make assertions about failures when failure section is missing"() {
        given:
        def output = """
some message.

broken!
"""
        def failure = new OutputScrapingExecutionFailure(output, "")

        when:
        failure.assertHasDescription("broken!")

        then:
        def e = thrown(AssertionError)
        e.message.trim().startsWith('Expected: a string starting with "broken!"')
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
        def failure = new OutputScrapingExecutionFailure(output, "")

        then:
        failure.assertOutputContains("Some message")
        failure.assertHasErrorOutput("Some error")

        when:
        failure.assertOutputContains("broken")

        then:
        def e = thrown(AssertionError)
        e.message.startsWith("Substring not found in build output")

        when:
        failure.assertHasErrorOutput("broken")

        then:
        def e2 = thrown(AssertionError)
        e2.message.startsWith("Substring not found in build output")
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
        def failure = new OutputScrapingExecutionFailure(output, "")

        then:
        failure.assertNotOutput("missing")

        when:
        failure.assertNotOutput("broken")

        then:
        def e = thrown(AssertionError)
        e.message.startsWith("Substring found in build output")
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
        def failure = new OutputScrapingExecutionFailure(output, "")

        then:
        failure.exception.class.simpleName == 'ServiceCreationException'
        failure.exception.message == 'Could not create service of type CacheLockingManager'
        failure.exception.cause.class.simpleName == 'IOException'
        failure.exception.cause.message == 'Something in the middle'
        failure.exception.cause.cause.class.simpleName == 'UncheckedIOException'
        failure.exception.cause.cause.message == "Unable to create directory 'metadata-2.1'"
    }

}
