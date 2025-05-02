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

import org.gradle.api.tasks.testing.logging.TestLogging
import org.gradle.internal.serialize.PlaceholderAssertionError
import org.gradle.internal.serialize.PlaceholderException
import spock.lang.Specification

class ShortExceptionFormatterTest extends Specification {
    def testDescriptor = new SimpleTestDescriptor()
    def testLogging = Mock(TestLogging)
    def formatter = new ShortExceptionFormatter(testLogging)

    def "shows all exceptions that have occurred for a test"() {
        def exceptions = [
            new IOException("oops").tap { stackTrace = createStackTrace() },
            // java.lang.AssertionError(String) is private, so explicit cast to Object is needed here
            // to call public AssertionError(Object)
            new AssertionError((Object) "ouch").tap { stackTrace = createCauseTrace() }
        ]
        expect:
        formatter.format(testDescriptor, exceptions) == """\
    java.io.IOException at FileName1.java:11
    java.lang.AssertionError at FileName0.java:1
"""
    }

    def "shows test entry point if it can be determined"() {
        def exception = new Exception("oops")
        testDescriptor.className = getClass().name

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception at ShortExceptionFormatterTest.groovy:44
"""
    }

    def "optionally shows causes"() {
        def causeCause = new RuntimeException("oops").tap { stackTrace = createCauseCauseTrace() }
        def cause = new IllegalArgumentException("ouch", causeCause).tap { stackTrace = createCauseTrace() }
        def exception = new Exception("argh", cause).tap { stackTrace = createStackTrace() }

        testLogging.showCauses >> true

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception at FileName1.java:11
        Caused by: java.lang.IllegalArgumentException at FileName0.java:1
            Caused by: java.lang.RuntimeException at FileName00.java:1
"""
    }

    def "formats placeholder exceptions correctly"() {
        given:
        testDescriptor.className = getClass().name

        expect:
        formatter.format(testDescriptor, [exception]).contains("java.lang.Exception at ShortExceptionFormatterTest.groovy")

        where:
        exception << [
            new PlaceholderException(Exception.class.name, "oops", null, "java.lang.Exception: oops", null, null),
            new PlaceholderAssertionError(Exception.class.name, "oops", null, "java.lang.Exception: oops", null, null)
        ]
    }

    private createStackTrace() {
        [
            new StackTraceElement("org.ClassName1", "methodName1", "FileName1.java", 11),
            new StackTraceElement("org.ClassName2", "methodName2", "FileName2.java", 22),
            new StackTraceElement("org.ClassName3", "methodName3", "FileName3.java", 33)
        ] as StackTraceElement[]
    }

    private createCauseTrace() {
        [
            new StackTraceElement("org.ClassName0", "methodName0", "FileName0.java", 1),
            new StackTraceElement("org.ClassName1", "methodName1", "FileName1.java", 10),
            new StackTraceElement("org.ClassName2", "methodName2", "FileName2.java", 22),
            new StackTraceElement("org.ClassName3", "methodName3", "FileName3.java", 33)
        ] as StackTraceElement[]
    }

    private createCauseCauseTrace() {
        [
            new StackTraceElement("org.ClassName01", "methodName00", "FileName00.java", 1),
            new StackTraceElement("org.ClassName01", "methodName01", "FileName01.java", 10),
            new StackTraceElement("org.ClassName02", "methodName02", "FileName02.java", 22),
            new StackTraceElement("org.ClassName03", "methodName03", "FileName03.java", 33)
        ] as StackTraceElement[]
    }
}
