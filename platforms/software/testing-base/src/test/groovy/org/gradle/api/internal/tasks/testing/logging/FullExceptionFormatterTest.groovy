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
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter
import org.gradle.internal.serialize.PlaceholderException
import org.gradle.util.TestUtil
import spock.lang.Specification

class FullExceptionFormatterTest extends Specification {
    def testDescriptor = new SimpleTestDescriptor()
    def testLogging = Mock(TestLogging) {
        getShowStackTraces() >> TestUtil.objectFactory().property(Boolean).convention(false)
        getShowCauses() >> TestUtil.objectFactory().property(Boolean).convention(false)
        getStackTraceFilters() >> TestUtil.objectFactory().setProperty(TestStackTraceFilter).convention(EnumSet.noneOf(TestStackTraceFilter))
    }
    def formatter = new FullExceptionFormatter(testLogging)

    def "shows all exceptions that have occurred for a test"() {
        expect:
        formatter.format(testDescriptor, [new RuntimeException("oops"), new Exception("ouch")]) == """\
    java.lang.RuntimeException: oops

    java.lang.Exception: ouch
"""
    }

    def "optionally shows causes"() {
        testLogging.getShowCauses().set(true)
        def cause = new RuntimeException("oops")
        def exception = new Exception("ouch", cause)

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch

        Caused by:
        java.lang.RuntimeException: oops
"""
    }

    def "optionally shows stack traces"() {
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.noneOf(TestStackTraceFilter))
        def exception = new Exception("ouch")
        exception.stackTrace = createStackTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at org.ClassName3.methodName3(FileName3.java:33)
"""
    }

    def "doesn't show common stack trace elements of parent trace and cause"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.noneOf(TestStackTraceFilter))

        def cause = new RuntimeException("oops")
        cause.stackTrace = createCauseTrace()
        def exception = new Exception("ouch", cause)
        exception.stackTrace = createStackTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at org.ClassName3.methodName3(FileName3.java:33)

        Caused by:
        java.lang.RuntimeException: oops
            at org.ClassName0.methodName0(FileName0.java:1)
            at org.ClassName1.methodName1(FileName1.java:10)
            ... 2 more
"""
    }

    def "always shows at least one stack trace element of cause"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.noneOf(TestStackTraceFilter))

        def cause = new RuntimeException("oops")
        cause.stackTrace = createStackTrace()
        def exception = new Exception("ouch", cause)
        exception.stackTrace = createStackTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at org.ClassName3.methodName3(FileName3.java:33)

        Caused by:
        java.lang.RuntimeException: oops
            at org.ClassName1.methodName1(FileName1.java:11)
            ... 2 more
"""
    }

    def "can cope with a cause that has fewer stack trace elements than parent exception"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.noneOf(TestStackTraceFilter))

        def cause = new RuntimeException("oops")
        cause.stackTrace = createStackTrace()[1..2]
        def exception = new Exception("ouch", cause)
        exception.stackTrace = createStackTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at org.ClassName3.methodName3(FileName3.java:33)

        Caused by:
        java.lang.RuntimeException: oops
            at org.ClassName2.methodName2(FileName2.java:22)
            ... 1 more
"""
    }

    def "shows all stack trace elements of cause if overlap doesn't start from bottom of trace"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.noneOf(TestStackTraceFilter))

        def cause = new RuntimeException("oops")
        cause.stackTrace = createStackTrace()[0..1]
        def exception = new Exception("ouch", cause)
        exception.stackTrace = createStackTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at org.ClassName3.methodName3(FileName3.java:33)

        Caused by:
        java.lang.RuntimeException: oops
            at org.ClassName1.methodName1(FileName1.java:11)
            at org.ClassName2.methodName2(FileName2.java:22)
"""
    }

    def "supports any combination of stack trace filters"() {
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.of(TestStackTraceFilter.TRUNCATE, TestStackTraceFilter.GROOVY))

        def exception = new Exception("ouch")
        exception.stackTrace = createGroovyTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at ClassName.testName(MyTest.java:22)
"""
    }

    def "retains stacktrace for inherited test classes"() {
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.of(TestStackTraceFilter.TRUNCATE, TestStackTraceFilter.GROOVY))
        testDescriptor.className = "foo"

        def exception = new Exception("ouch")
        exception.stackTrace = createGroovyTrace()

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at java.lang.reflect.Method.invoke(Method.java:597)
        at org.ClassName2.methodName2(FileName2.java:22)
        at ClassName.testName(MyTest.java:22)
        at java.lang.reflect.Method.invoke(Method.java:597)
        at org.ClassName3.methodName3(FileName3.java:33)
"""
    }

    def "treat anonymous class and its enclosing class equally"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.of(TestStackTraceFilter.TRUNCATE, TestStackTraceFilter.GROOVY))

        def exception = new PlaceholderException(Exception.name, "ouch", null, "java.lang.Exception: ouch", null, null)
        def stacktrace = createGroovyTrace()
        stacktrace[3] = new StackTraceElement('ClassName$1$1', "whatever", "MyTest.java", 22)
        exception.stackTrace = stacktrace

        expect:
        formatter.format(testDescriptor, [exception]) == '''\
    java.lang.Exception: ouch
        at org.ClassName1.methodName1(FileName1.java:11)
        at org.ClassName2.methodName2(FileName2.java:22)
        at ClassName$1$1.whatever(MyTest.java:22)
'''
    }

    def "also filters stack traces of causes"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.of(TestStackTraceFilter.ENTRY_POINT))

        def cause = new RuntimeException("oops")
        cause.stackTrace = createGroovyTrace()

        def exception = new Exception("ouch", cause)
        exception.stackTrace = createGroovyTrace()[1..-1]

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at ClassName.testName(MyTest.java:22)

        Caused by:
        java.lang.RuntimeException: oops
            at ClassName.testName(MyTest.java:22)
"""
    }

    def "formats PlaceholderException correctly"() {
        testLogging.getShowCauses().set(true)
        testLogging.getShowStackTraces().set(true)
        testLogging.getStackTraceFilters().set(EnumSet.of(TestStackTraceFilter.ENTRY_POINT))

        def cause = new PlaceholderException(RuntimeException.name, "oops", null, "java.lang.RuntimeException: oops", null, null)
        cause.stackTrace = createGroovyTrace()

        def exception = new PlaceholderException(Exception.name, "ouch", null, "java.lang.Exception: ouch", null, cause)
        exception.stackTrace = createGroovyTrace()[1..-1]

        expect:
        formatter.format(testDescriptor, [exception]) == """\
    java.lang.Exception: ouch
        at ClassName.testName(MyTest.java:22)

        Caused by:
        java.lang.RuntimeException: oops
            at ClassName.testName(MyTest.java:22)
"""
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

    private createGroovyTrace() {
        [
                new StackTraceElement("org.ClassName1", "methodName1", "FileName1.java", 11),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 597),
                new StackTraceElement("org.ClassName2", "methodName2", "FileName2.java", 22),
                // class and method name match SimpleTestDescriptor
                new StackTraceElement("ClassName", "testName", "MyTest.java", 22),
                new StackTraceElement("java.lang.reflect.Method", "invoke", "Method.java", 597),
                new StackTraceElement("org.ClassName3", "methodName3", "FileName3.java", 33)
        ] as StackTraceElement[]
    }
}
