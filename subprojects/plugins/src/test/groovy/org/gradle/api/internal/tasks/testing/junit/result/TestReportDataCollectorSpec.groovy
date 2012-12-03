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

import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification
import org.gradle.api.internal.tasks.testing.*

import static java.util.Arrays.asList
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut
import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
import static org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS

import static com.google.common.collect.Lists.newLinkedList

/**
 * by Szczepan Faber, created at: 11/19/12
 */
class TestReportDataCollectorSpec extends Specification {

    @Rule private TemporaryFolder temp = new TemporaryFolder()
    private collector = new TestReportDataCollector(temp.dir)

    def "validates results directory"() {
        temp.file("foo.txt").createNewFile()
        temp.file("empty").createDir()

        when:
        new TestReportDataCollector(new File("don't exist!"))
        then:
        thrown(IllegalArgumentException);

        when:
        new TestReportDataCollector(temp.dir)
        then:
        thrown(IllegalArgumentException);

        when:
        new TestReportDataCollector(temp.file("empty"))
        then:
        noExceptionThrown()
    }

    def "closes all files when root finishes"() {
        collector.cachingFileWriter = Mock(CachingFileWriter)

        def root = new DefaultTestSuiteDescriptor("1", "Suite")
        def clazz = new DecoratingTestDescriptor(new DefaultTestClassDescriptor("1.1", "Class"), root)

        def dummyResult = new DefaultTestResult(SUCCESS, 0, 0, 0, 0, 0, asList())

        when:
        collector.afterSuite(clazz, dummyResult)

        then:
        0 * collector.cachingFileWriter.closeAll()

        when:
        collector.afterSuite(root, dummyResult)

        then:
        1 * collector.cachingFileWriter.closeAll()
    }

    def "closes files after test"() {
        collector.cachingFileWriter = Mock(CachingFileWriter)
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")

        when:
        collector.afterTest(test, Mock(TestResult))

        then:
        1 * collector.cachingFileWriter.close(new File(temp.dir, "FooTest.stderr"))
        1 * collector.cachingFileWriter.close(new File(temp.dir, "FooTest.stdout"))
        0 * collector._
    }

    def "keeps track of test results"() {
        def root = new DefaultTestSuiteDescriptor("1", "Suite")
        def clazz = new DecoratingTestDescriptor(new DefaultTestClassDescriptor("1.1", "FooTest"), root)
        def test1 = new DecoratingTestDescriptor(new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod"), clazz)
        def result1 = new DefaultTestResult(SUCCESS, 100, 200, 1, 1, 0, asList())

        def test2 = new DecoratingTestDescriptor(new DefaultTestDescriptor("1.1.2", "FooTest", "testMethod2"), clazz)
        def result2 = new DefaultTestResult(FAILURE, 250, 300, 1, 0, 1, asList(new RuntimeException("Boo!")))

        when:
        //simulating TestNG, where we don't receive beforeSuite for classes
        collector.beforeSuite(root)

        collector.beforeTest(test1)
        collector.beforeTest(test2)

        collector.afterTest(test1, result1)
        collector.afterTest(test2, result2)

        collector.afterSuite(root, new DefaultTestResult(FAILURE, 0, 500, 2, 1, 1, asList(new RuntimeException("Boo!"))))

        then:
        def results = collector.getResults()
        results.size() == 1
        def fooTest = results['FooTest']
        fooTest.startTime == 100
        fooTest.testsCount == 2
        fooTest.failuresCount == 1
        fooTest.duration == 200
        fooTest.results.size() == 2
        fooTest.results.find { it.name == 'testMethod' && it.result == result1 && it.duration == 100 }
        fooTest.results.find { it.name == 'testMethod2' && it.result == result2 && it.duration == 50 }
    }

    def "keeps track of outputs"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def test2 = new DefaultTestDescriptor("1.1.2", "FooTest", "testMethod2")
        def suite = new DefaultTestSuiteDescriptor("1", "Suite")

        collector.cachingFileWriter = Mock(CachingFileWriter)

        when:
        collector.onOutput(suite, new DefaultTestOutputEvent(StdOut, "out"))
        collector.onOutput(test, new DefaultTestOutputEvent(StdErr, "err"))
        collector.onOutput(test2, new DefaultTestOutputEvent(StdOut, "out"))

        then:
        1 * collector.cachingFileWriter.write(new File(temp.dir, "FooTest.stderr"), "err")
        1 * collector.cachingFileWriter.write(new File(temp.dir, "FooTest.stdout"), "out")
        0 * collector.cachingFileWriter._
    }

    def "keeps track output in right format"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        collector.cachingFileWriter = Mock(CachingFileWriter)

        when:
        collector.onOutput(test, new DefaultTestOutputEvent(StdErr, "hey ]]> foo"))

        then:
        1 * collector.cachingFileWriter.write(new File(temp.dir, "FooTest.stderr"), "hey ]]> foo")
    }

    def "provides outputs"() {
        def test = new DefaultTestDescriptor("1.1.1", "FooTest", "testMethod")
        def test2 = new DefaultTestDescriptor("1.1.2", "FooTest", "testMethod2")
        def test3 = new DefaultTestDescriptor("1.1.3", "BarTest", "testMethod")

        when:
        collector.onOutput(test, new DefaultTestOutputEvent(StdErr, "err"))
        collector.onOutput(test, new DefaultTestOutputEvent(StdErr, "err2"))
        collector.onOutput(test2, new DefaultTestOutputEvent(StdOut, "out"))
        collector.onOutput(test3, new DefaultTestOutputEvent(StdOut, "out, don't show"))

        collector.afterSuite(new DefaultTestSuiteDescriptor("1", "suite"), null) //force closing of files

        then:
        newLinkedList(collector.getOutputs("FooTest", StdErr)) == ['err', 'err2']
        newLinkedList(collector.getOutputs("FooTest", StdOut)) == ['out']
    }
}
