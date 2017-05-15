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

import org.gradle.api.internal.tasks.testing.SimpleTestResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory
import org.gradle.internal.progress.BuildOperationDescriptor
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.BuildOperationState
import org.gradle.internal.progress.BuildOperationType
import org.gradle.internal.progress.DefaultBuildOperationExecutor
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

import java.util.concurrent.Executor

class TestEventLoggerTest extends Specification {
    def textOutputFactory = new TestStyledTextOutputFactory()

    def testLogging = new DefaultTestLogging()
    def exceptionFormatter = Mock(TestExceptionFormatter)
    WorkerLeaseService workerLeaseService = Stub(WorkerLeaseService)
    def buildOperationExecutor = createBuildOperationExecutor()
    def parentOperation = createParentOperation()
    def eventLogger = new TestEventLogger(textOutputFactory, LogLevel.INFO, testLogging, exceptionFormatter, buildOperationExecutor, parentOperation)

    def rootDescriptor = new SimpleTestDescriptor(name: "", composite: true)
    def workerDescriptor = new SimpleTestDescriptor(name: "worker", composite: true, parent: rootDescriptor)
    def outerSuiteDescriptor = new SimpleTestDescriptor(name: "com.OuterSuiteClass", composite: true, parent: workerDescriptor)
    def innerSuiteDescriptor = new SimpleTestDescriptor(name: "com.InnerSuiteClass", composite: true, parent: outerSuiteDescriptor)
    def classDescriptor = new SimpleTestDescriptor(name: "foo.bar.TestClass", composite: true, parent: innerSuiteDescriptor)
    def methodDescriptor = new SimpleTestDescriptor(name: "testMethod", className: "foo.bar.TestClass", parent: classDescriptor)

    def result = new SimpleTestResult()

    private BuildOperationExecutor createBuildOperationExecutor() {
        new DefaultBuildOperationExecutor(
            Mock(BuildOperationListener), Mock(TimeProvider), new NoOpProgressLoggerFactory(),
            new DefaultBuildOperationQueueFactory(workerLeaseService), new DefaultExecutorFactory(), 1)
    }

    private BuildOperationState createParentOperation() {
        BuildOperationDescriptor buildOperationDescriptor = new BuildOperationDescriptor(123, 456, 'parent', 'Parent Operation', 'Parent Operation', null, BuildOperationType.TASK)
        def parentOperation = new DefaultBuildOperationExecutor.DefaultBuildOperationState(buildOperationDescriptor, new Date().time)
        parentOperation.setRunning(true)
        parentOperation
    }

    def setup() {
        _ * workerLeaseService.withLocks(_) >> { args ->
            new Executor() {
                @Override
                void execute(Runnable runnable) {
                    runnable.run()
                }
            }
        }
    }

    def "logs event if event type matches"() {
        testLogging.events(TestLogEvent.PASSED, TestLogEvent.SKIPPED)

        when:
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 1

        when:
        textOutputFactory.clear()
        result.resultType = TestResult.ResultType.FAILURE
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 0
    }

    def "logs event if granularity matches"() {
        testLogging.events(TestLogEvent.PASSED)
        testLogging.minGranularity = 2
        testLogging.maxGranularity = 4

        when:
        eventLogger.afterSuite(outerSuiteDescriptor, result)
        eventLogger.afterSuite(innerSuiteDescriptor, result)
        eventLogger.afterSuite(classDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 3

        when:
        textOutputFactory.clear()
        eventLogger.afterSuite(rootDescriptor, result)
        eventLogger.afterSuite(workerDescriptor, result)
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().count("PASSED") == 0
    }

    def "shows exceptions if configured"() {
        testLogging.events(TestLogEvent.FAILED)
        result.resultType = TestResult.ResultType.FAILURE
        result.exceptions = [new RuntimeException()]

        exceptionFormatter.format(*_) >> "formatted exception"

        when:
        testLogging.showExceptions = true
        eventLogger.afterTest(methodDescriptor, result)

        then:
        textOutputFactory.toString().contains("formatted exception")

        when:
        textOutputFactory.clear()
        testLogging.showExceptions = false
        eventLogger.afterTest(methodDescriptor, result)

        then:
        !textOutputFactory.toString().contains("formatted exception")
    }
}
