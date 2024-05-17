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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.internal.tasks.testing.*
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.internal.serialize.PlaceholderException
import spock.lang.Issue
import spock.lang.Specification

import static java.util.Arrays.asList
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut
import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
import static org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS

class TestReportDataCollectorSpec extends Specification {
    def Map<String, TestClassResult> results = [:]
    def TestOutputStore.Writer writer = Mock()
    def collector = new TestReportDataCollector(results, writer)

    def "keeps track of test results"() {
        def root = new DefaultTestSuiteDescriptor("1", "Suite")
        def clazz = new DecoratingTestDescriptor(new DefaultTestClassDescriptor("1.1", "FooTest"), root)
        def test1 = new DecoratingTestDescriptor(new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod"), clazz)
        def result1 = new DefaultTestResult(SUCCESS, 100, 200, 1, 1, 0, [])

        def test2 = new DecoratingTestDescriptor(new DefaultTestDescriptor("1.1.2", "FooTest", "testMethod2"), clazz)
        def result2 = new DefaultTestResult(FAILURE, 250, 300, 1, 0, 1, asList(org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("Boo!"))))

        when:
        //simulating TestNG, where we don't receive beforeSuite for classes
        collector.beforeSuite(root)

        collector.beforeTest(test1)
        collector.beforeTest(test2)

        collector.afterTest(test1, result1)
        collector.afterTest(test2, result2)

        collector.afterSuite(root, new DefaultTestResult(FAILURE, 0, 500, 2, 1, 1, []))

        then:
        results.size() == 1
        def fooTest = results.values().toList().first()
        fooTest.className == 'FooTest'
        fooTest.startTime == 100
        fooTest.testsCount == 2
        fooTest.failuresCount == 1
        fooTest.duration == 200
        fooTest.results.size() == 2
        fooTest.results.find { it.name == 'testMethod' && it.endTime == 200 && it.duration == 100 }
        fooTest.results.find { it.name == 'testMethod2' && it.endTime == 300 && it.duration == 50 }
    }

    def "writes test outputs for interleaved tests"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def test2 = new DefaultTestDescriptor("1.1.2", "FooTest", "testMethod2")
        def suite = new DefaultTestSuiteDescriptor("1", "Suite")

        when:
        collector.onOutput(suite, new DefaultTestOutputEvent(StdOut, "suite-out"))
        collector.beforeTest(test)
        collector.beforeTest(test2)
        collector.onOutput(test, new DefaultTestOutputEvent(StdErr, "err-1"))
        collector.onOutput(test2, new DefaultTestOutputEvent(StdOut, "out-2"))
        collector.onOutput(test, new DefaultTestOutputEvent(StdOut, "out-1"))

        then:
        1 * writer.onOutput(3, 1, new DefaultTestOutputEvent(StdErr, "err-1"))
        1 * writer.onOutput(3, 2, new DefaultTestOutputEvent(StdOut, "out-2"))
        1 * writer.onOutput(3, 1, new DefaultTestOutputEvent(StdOut, "out-1"))
        0 * writer._
    }

    def "writes test outputs for class"() {
        def testClass = new DefaultTestClassDescriptor("1.1.1", "FooTest")
        def suite = new DefaultTestSuiteDescriptor("1", "Suite")

        when:
        collector.onOutput(suite, new DefaultTestOutputEvent(StdOut, "suite-out"))
        collector.onOutput(testClass, new DefaultTestOutputEvent(StdErr, "err-1"))
        collector.onOutput(testClass, new DefaultTestOutputEvent(StdErr, "err-2"))

        then:
        1 * writer.onOutput(1, new DefaultTestOutputEvent(StdErr, "err-1"))
        1 * writer.onOutput(1, new DefaultTestOutputEvent(StdErr, "err-2"))
        0 * writer._
    }

    def "writes test outputs for failed suite"() {
        def suite = new DefaultTestSuiteDescriptor("1", "Suite")
        def failure = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("failure"))
        def result = new DefaultTestResult(FAILURE, 0, 0, 0, 0, 0, [failure])

        when:
        collector.beforeSuite(suite)
        collector.onOutput(suite, new DefaultTestOutputEvent(StdOut, "suite-out"))
        collector.afterSuite(suite, result)

        then:
        1 * writer.onOutput(_, _, new DefaultTestOutputEvent(StdOut, "suite-out"))
        0 * writer._
    }

    def "collects failures for test"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def failure1 = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("failure1"))
        def failure2 = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new IOException("failure2"))
        def result = new DefaultTestResult(SUCCESS, 0, 0, 1, 0, 1, [failure1, failure2])

        when:
        collector.beforeTest(test)
        collector.afterTest(test, result)

        then:
        def failures = results["FooTest"].results[0].failures
        failures.size() == 2
        failures[0].exceptionType == RuntimeException.name
        failures[0].message == failure1.rawFailure.toString()
        failures[0].stackTrace.startsWith(failure1.rawFailure.toString())
        failures[1].exceptionType == IOException.name
        failures[1].message == failure2.rawFailure.toString()
        failures[1].stackTrace.startsWith(failure2.rawFailure.toString())
    }

    def "handle PlaceholderExceptions for test failures"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def failure = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new PlaceholderException("OriginalClassName", "failure2", null, "toString()", null, null))
        def result = new DefaultTestResult(SUCCESS, 0, 0, 1, 0, 1, [failure])

        when:
        collector.beforeTest(test)
        collector.afterTest(test, result)

        then:
        def failures = results["FooTest"].results[0].failures
        failures.size() == 1
        failures[0].exceptionType == "OriginalClassName"
        failures[0].message == "toString()"
        failures[0].stackTrace.startsWith("toString()")
    }

    def "handles exception whose toString() method fails"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def failure2 = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("failure2"))
        def failure1 = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("failure1") {
            @Override
            String toString() {
                throw failure2.rawFailure
            }
        })
        def result = new DefaultTestResult(SUCCESS, 0, 0, 1, 0, 1, [failure1])

        when:
        collector.beforeTest(test)
        collector.afterTest(test, result)

        then:
        def failures = results["FooTest"].results[0].failures
        failures.size() == 1
        failures[0].message == "Could not determine failure message for exception of type ${failure1.rawFailure.class.name}: ${failure2.rawFailure.toString()}"
        failures[0].stackTrace.startsWith(failure2.rawFailure.toString())
    }

    def "handles exception whose printStackTrace() method fails"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def failure2 = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("failure2"))
        def failure1 = org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("failure1") {
            @Override
            void printStackTrace(PrintWriter s) {
                throw failure2.rawFailure
            }
        })
        def result = new DefaultTestResult(SUCCESS, 0, 0, 1, 0, 1, [failure1])

        when:
        collector.beforeTest(test)
        collector.afterTest(test, result)

        then:
        def failures = results["FooTest"].results[0].failures
        failures.size() == 1
        failures[0].message == failure1.rawFailure.toString()
        failures[0].stackTrace.startsWith(failure2.rawFailure.toString())
    }

    def "reports suite failures"() {
        def root = new DefaultTestSuiteDescriptor("1", "Suite")
        def testWorker = new DefaultTestSuiteDescriptor("2", "Test Worker 1")

        when:
        //simulating a scenario with suite failing badly enough so that no tests are executed
        collector.beforeSuite(root)
        collector.beforeSuite(testWorker)
        collector.afterSuite(testWorker, new DefaultTestResult(FAILURE, 50, 450, 2, 1, 1, [org.gradle.api.tasks.testing.TestFailure.fromTestFrameworkFailure(new RuntimeException("Boo!"))]))
        collector.afterSuite(root, new DefaultTestResult(FAILURE, 0, 500, 2, 1, 1, []))

        then:
        results.size() == 1
        def result = results.values().toList().first()
        result.className == 'Test Worker 1'
        result.startTime == 50
        result.testsCount == 1
        result.failuresCount == 1
        result.duration == 400
        result.results.size() == 1
        result.results[0].failures.size() == 1
    }

    @Issue("GRADLE-2730")
    def "test case timestamp is correct even if output received for given class"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")

        when:
        collector.beforeTest(test)
        collector.onOutput(test, new DefaultTestOutputEvent(StdOut, "suite-out"))
        collector.afterTest(test, new DefaultTestResult(SUCCESS, 100, 200, 1, 1, 0, asList()))

        then:
        results.get("FooTest").startTime == 100
    }
}
