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

package org.gradle.api.internal.tasks.testing.logging

import spock.lang.Specification
import org.gradle.api.tasks.testing.logging.TestLogging

class ShortExceptionFormatterTest extends Specification {
    def testDescriptor = new SimpleTestDescriptor()
    def testLogging = Mock(TestLogging)
    def formatter = new ShortExceptionFormatter(testLogging)

    def "shows all exceptions that have occurred for a test"() {
        expect:
        formatter.format(testDescriptor, [new IOException(), new AssertionError()]) == """\
    java.io.IOException
    java.lang.AssertionError
"""
    }

    def "shows test entry point if it can be determined"() {
        def exception = new Exception()
        testDescriptor.className = getClass().name

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception at ShortExceptionFormatterTest.groovy:36
"""
    }

    def "optionally shows causes"() {
        def causeCause = new RuntimeException()
        def cause = new IllegalArgumentException(causeCause)
        def exception = new Exception(cause)

        testLogging.showCauses >> true

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception
        Caused by: java.lang.IllegalArgumentException
            Caused by: java.lang.RuntimeException
"""
    }
}
