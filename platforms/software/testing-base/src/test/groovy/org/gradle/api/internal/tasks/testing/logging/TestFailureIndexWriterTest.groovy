/*
 * Copyright 2025 the original author or authors.
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

import groovy.json.JsonSlurper
import org.gradle.api.internal.tasks.testing.AssertionFailureDetails
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestFailure
import org.gradle.api.internal.tasks.testing.DefaultTestFailureDetails
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.internal.tasks.testing.source.DefaultClassSource
import org.gradle.api.internal.tasks.testing.source.DefaultFilePosition
import org.gradle.api.internal.tasks.testing.source.DefaultFileSource
import org.gradle.api.internal.tasks.testing.source.DefaultMethodSource
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class TestFailureIndexWriterTest extends Specification {
    @TempDir
    Path tempDir

    def "writes failure index JSON on root suite completion"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "testSomething", "com.example.MyTest", "testSomething")

        def stacktrace = """\
java.lang.AssertionError: expected <true> but was <false>
\tat com.example.MyTest.testSomething(MyTest.java:15)
\tat java.lang.reflect.Method.invoke(Method.java:498)
\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:52)"""

        def details = new AssertionFailureDetails("expected <true> but was <false>", "java.lang.AssertionError", stacktrace, "true", "false")
        def failure = new DefaultTestFailure(new AssertionError("expected <true> but was <false>"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)

        def rootResult = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null)
        writer.completed(rootSuite, rootResult, complete)

        then:
        outputFile.toFile().exists()
        def json = new JsonSlurper().parse(outputFile.toFile())
        json.version == 1
        json.failures.size() == 1

        def f = json.failures[0]
        f.className == "com.example.MyTest"
        f.displayName == "testSomething"
        f.failure.type == "assertion"
        f.failure.exceptionClass == "java.lang.AssertionError"
        f.failure.message == "expected <true> but was <false>"
        f.failure.messageComplete == true
        f.failure.isComparison == true
        f.failure.expected == "true"
        f.failure.actual == "false"
        f.failure.stackTraceFrameCount == 1 // filtered to test class only
        f.failure.totalFrameCount == 3
        f.filter == "com.example.MyTest.testSomething"
    }

    def "does not write index when no failures"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def rootResult = new DefaultTestResult(TestResult.ResultType.SUCCESS, 0, 0, 1, 1, 0, [], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.SUCCESS)

        when:
        writer.completed(rootSuite, rootResult, complete)

        then:
        !outputFile.toFile().exists()
        !writer.hasFailures()
    }

    def "captures stdout and stderr"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "testSomething", "com.example.MyTest", "testSomething")

        def details = new DefaultTestFailureDetails("error", "RuntimeException", "stack")
        def failure = new DefaultTestFailure(new RuntimeException("error"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        def stdoutEvent = Mock(TestOutputEvent) {
            getDestination() >> TestOutputEvent.Destination.StdOut
            getMessage() >> "hello stdout\n"
        }
        def stderrEvent = Mock(TestOutputEvent) {
            getDestination() >> TestOutputEvent.Destination.StdErr
            getMessage() >> "hello stderr\n"
        }

        when:
        writer.output(descriptor, stdoutEvent)
        writer.output(descriptor, stderrEvent)
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        json.failures[0].output.stdout == "hello stdout\n"
        json.failures[0].output.stderr == "hello stderr\n"
    }

    def "filters stacktrace to test class"() {
        def fullTrace = """\
java.lang.IllegalStateException: connection refused
\tat com.example.HttpClient.connect(HttpClient.java:42)
\tat com.example.UserService.fetchUser(UserService.java:28)
\tat com.example.MyTest.testFetchUser(MyTest.java:15)
\tat java.lang.reflect.Method.invoke(Method.java:498)
\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:52)"""

        when:
        def filtered = TestFailureIndexWriter.filterStackTrace(fullTrace, "com.example.MyTest")

        then:
        filtered == """\
java.lang.IllegalStateException: connection refused
\tat com.example.HttpClient.connect(HttpClient.java:42)
\tat com.example.UserService.fetchUser(UserService.java:28)
\tat com.example.MyTest.testFetchUser(MyTest.java:15)"""
    }

    def "escapes JSON special characters"() {
        expect:
        TestFailureIndexWriter.escapeJson('hello "world"') == 'hello \\"world\\"'
        TestFailureIndexWriter.escapeJson("line1\nline2") == "line1\\nline2"
        TestFailureIndexWriter.escapeJson("path\\to\\file") == "path\\\\to\\\\file"
        TestFailureIndexWriter.escapeJson("tab\there") == "tab\\there"
    }

    def "truncates long messages"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "test", "com.example.MyTest", "test")

        def longMessage = "x" * 2000
        def details = new AssertionFailureDetails(longMessage, "java.lang.AssertionError", "stack", null, null)
        def failure = new DefaultTestFailure(new AssertionError(longMessage), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        json.failures[0].failure.message.length() == 1024
        json.failures[0].failure.messageComplete == false
    }

    def "includes FileSource with file path and line number"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def fileSource = new DefaultFileSource(new File("src/test/java/com/example/MyTest.java"), new DefaultFilePosition(15, 5))
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "testSomething", fileSource)

        def details = new DefaultTestFailureDetails("error", "RuntimeException", "stack")
        def failure = new DefaultTestFailure(new RuntimeException("error"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        def source = json.failures[0].source
        source.type == "file"
        source.file == "src/test/java/com/example/MyTest.java"
        source.line == 15
        source.column == 5
    }

    def "includes MethodSource with class and method names"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def methodSource = new DefaultMethodSource("com.example.MyTest", "testSomething")
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "testSomething", methodSource)

        def details = new DefaultTestFailureDetails("error", "RuntimeException", "stack")
        def failure = new DefaultTestFailure(new RuntimeException("error"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        def source = json.failures[0].source
        source.type == "method"
        source.className == "com.example.MyTest"
        source.methodName == "testSomething"
    }

    def "includes ClassSource with class name"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def classSource = new DefaultClassSource("com.example.MyTest")
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "testSomething", classSource)

        def details = new DefaultTestFailureDetails("error", "RuntimeException", "stack")
        def failure = new DefaultTestFailure(new RuntimeException("error"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        def source = json.failures[0].source
        source.type == "class"
        source.className == "com.example.MyTest"
    }

    def "source is null for NoSource"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        // DefaultTestDescriptor with 5-arg constructor uses NoSource
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "testSomething", "com.example.MyTest", "testSomething")

        def details = new DefaultTestFailureDetails("error", "RuntimeException", "stack")
        def failure = new DefaultTestFailure(new RuntimeException("error"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        json.failures[0].source == null
    }

    def "framework failures have correct type"() {
        def outputFile = tempDir.resolve("failure-index.json")
        def writer = new TestFailureIndexWriter(outputFile)

        def rootSuite = new DefaultTestSuiteDescriptor(0, "Root")
        def descriptor = new DefaultTestDescriptor(1, "com.example.MyTest", "test", "com.example.MyTest", "test")

        def details = new DefaultTestFailureDetails("connection refused", "java.net.ConnectException", "stack")
        def failure = new DefaultTestFailure(new RuntimeException("connection refused"), details, [])
        def result = new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [failure], null)
        def complete = new TestCompleteEvent(0, TestResult.ResultType.FAILURE)

        when:
        writer.completed(descriptor, result, complete)
        writer.completed(rootSuite, new DefaultTestResult(TestResult.ResultType.FAILURE, 0, 0, 1, 0, 1, [], null), complete)

        then:
        def json = new JsonSlurper().parse(outputFile.toFile())
        json.failures[0].failure.type == "framework"
        json.failures[0].failure.exceptionClass == "java.net.ConnectException"
    }
}
