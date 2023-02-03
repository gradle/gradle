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

package org.gradle.api.internal.tasks.testing.logging

import groovy.transform.TupleConstructor
import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import spock.lang.Specification

class TestWorkerProgressListenerTest extends Specification {

    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def parentProgressLogger = Mock(ProgressLogger)
    def testWorkerProgressListener = new TestWorkerProgressListener(progressLoggerFactory, parentProgressLogger)

    def "does not create progress logger for non-test worker test descriptors"() {
        given:
        def testDescriptor = new DefaultTestSuiteDescriptor(1, 'org.gradle.TestSuite')

        when:
        testWorkerProgressListener.started(testDescriptor, createTestStartEvent())

        then:
        testWorkerProgressListener.testWorkerProgressLoggers.isEmpty()
    }

    def "registers progress logger for test worker only once"() {
        def testWorkerProgressLogger = Mock(ProgressLogger)

        given:
        def testEvent = new TestEvent('Gradle Test Executor 1', 'org.gradle.Test1')
        def testDescriptor = createTestDescriptor(testEvent)

        when:
        testWorkerProgressListener.started(testDescriptor, createTestStartEvent())

        then:
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger
        1 * testWorkerProgressLogger.start(testEvent.progressLoggerDescription, testEvent.progressLoggerDescription)
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 1
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent.progressLoggerDescription) == testWorkerProgressLogger

        when:
        testWorkerProgressListener.started(testDescriptor, createTestStartEvent())

        then:
        0 * progressLoggerFactory._
        0 * testWorkerProgressLogger._
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 1
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent.progressLoggerDescription) == testWorkerProgressLogger
    }

    def "can register progress loggers for different test workers"() {
        def testWorkerProgressLogger1 = Mock(ProgressLogger)
        def testWorkerProgressLogger2 = Mock(ProgressLogger)

        given:
        def testEvent1 = new TestEvent('Gradle Test Executor 1', 'org.gradle.Test1')
        def testEvent2 = new TestEvent('Gradle Test Executor 2', 'org.gradle.Test2')
        def testDescriptor1 = createTestDescriptor(testEvent1)
        def testDescriptor2 = createTestDescriptor(testEvent2)

        when:
        testWorkerProgressListener.started(testDescriptor1, createTestStartEvent())

        then:
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger1
        1 * testWorkerProgressLogger1.start(testEvent1.progressLoggerDescription, testEvent1.progressLoggerDescription)
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 1
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent1.progressLoggerDescription) == testWorkerProgressLogger1

        when:
        testWorkerProgressListener.started(testDescriptor2, createTestStartEvent())

        then:
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger2
        1 * testWorkerProgressLogger2.start(testEvent2.progressLoggerDescription, testEvent2.progressLoggerDescription)
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 2
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent2.progressLoggerDescription) == testWorkerProgressLogger2
    }

    def "does not complete progress logger for test worker that hasn't been registered"() {
        given:
        def testEvent = new TestEvent('Gradle Test Executor 1', 'org.gradle.Test1')
        def testDescriptor = createTestDescriptor(testEvent)

        when:
        testWorkerProgressListener.completed(testDescriptor, createTestResult(), createTestCompleteEvent())

        then:
        0 * progressLoggerFactory._
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 0
    }

    def "completes progress loggers for test workers"() {
        def testWorkerProgressLogger1 = Mock(ProgressLogger)
        def testWorkerProgressLogger2 = Mock(ProgressLogger)

        given:
        def testEvent1 = new TestEvent('Gradle Test Executor 1', 'org.gradle.Test1')
        def testEvent2 = new TestEvent('Gradle Test Executor 2', 'org.gradle.Test2')
        def testDescriptor1 = createTestDescriptor(testEvent1)
        def testDescriptor2 = createTestDescriptor(testEvent2)

        when:
        testWorkerProgressListener.started(testDescriptor1, createTestStartEvent())
        testWorkerProgressListener.started(testDescriptor2, createTestStartEvent())

        then:
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger1
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger2
        1 * testWorkerProgressLogger1.start(testEvent1.progressLoggerDescription, testEvent1.progressLoggerDescription)
        1 * testWorkerProgressLogger2.start(testEvent2.progressLoggerDescription, testEvent2.progressLoggerDescription)
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 2
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent1.progressLoggerDescription) == testWorkerProgressLogger1
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent2.progressLoggerDescription) == testWorkerProgressLogger2

        when:
        testWorkerProgressListener.completed(testDescriptor1, createTestResult(), createTestCompleteEvent())
        testWorkerProgressListener.completed(testDescriptor2, createTestResult(), createTestCompleteEvent())

        then:
        1 * testWorkerProgressLogger1.completed()
        1 * testWorkerProgressLogger2.completed()
        testWorkerProgressListener.testWorkerProgressLoggers.isEmpty()
    }

    def "can complete all registered progress loggers"() {
        def testWorkerProgressLogger1 = Mock(ProgressLogger)
        def testWorkerProgressLogger2 = Mock(ProgressLogger)

        given:
        def testEvent1 = new TestEvent('Gradle Test Executor 1', 'org.gradle.Test1')
        def testEvent2 = new TestEvent('Gradle Test Executor 2', 'org.gradle.Test2')
        def testDescriptor1 = createTestDescriptor(testEvent1)
        def testDescriptor2 = createTestDescriptor(testEvent2)

        when:
        testWorkerProgressListener.started(testDescriptor1, createTestStartEvent())
        testWorkerProgressListener.started(testDescriptor2, createTestStartEvent())

        then:
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger1
        1 * progressLoggerFactory.newOperation(TestWorkerProgressListener.class, parentProgressLogger) >> testWorkerProgressLogger2
        1 * testWorkerProgressLogger1.start(testEvent1.progressLoggerDescription, testEvent1.progressLoggerDescription)
        1 * testWorkerProgressLogger2.start(testEvent2.progressLoggerDescription, testEvent2.progressLoggerDescription)
        testWorkerProgressListener.testWorkerProgressLoggers.size() == 2
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent1.progressLoggerDescription) == testWorkerProgressLogger1
        testWorkerProgressListener.testWorkerProgressLoggers.get(testEvent2.progressLoggerDescription) == testWorkerProgressLogger2

        when:
        testWorkerProgressListener.completeAll()

        then:
        1 * testWorkerProgressLogger1.completed()
        1 * testWorkerProgressLogger2.completed()
        testWorkerProgressListener.testWorkerProgressLoggers.isEmpty()
    }

    static TestStartEvent createTestStartEvent() {
        new TestStartEvent(new Date().time)
    }

    static TestResult createTestResult() {
        new DefaultTestResult(TestResult.ResultType.SUCCESS, new Date().time, new Date().time, 2L, 2L, 0L, [])
    }

    static TestCompleteEvent createTestCompleteEvent() {
        new TestCompleteEvent(new Date().time)
    }

    static TestDescriptor createTestDescriptor(TestEvent testEvent) {
        def testWorkerDescriptor = new WorkerTestClassProcessor.WorkerTestSuiteDescriptor(1, testEvent.testWorkerName)
        def defaultTestClassDescriptor = new DefaultTestClassDescriptor(1, testEvent.testClassName)
        def decoratingDefaultTestClassDescriptor = new DecoratingTestDescriptor(defaultTestClassDescriptor, null)
        def decoratingTestWorkerDescriptor = new DecoratingTestDescriptor(testWorkerDescriptor, decoratingDefaultTestClassDescriptor)
        new DecoratingTestDescriptor(defaultTestClassDescriptor, decoratingTestWorkerDescriptor)
    }

    @TupleConstructor
    private static class TestEvent {
        String testWorkerName
        String testClassName

        String getProgressLoggerDescription() {
            "Executing test $testClassName".toString()
        }
    }
}
