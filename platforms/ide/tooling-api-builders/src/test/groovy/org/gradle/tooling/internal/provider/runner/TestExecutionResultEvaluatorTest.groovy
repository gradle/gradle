/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.api.tasks.testing.TestExecutionException
import org.gradle.api.tasks.testing.TestResult
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.internal.build.event.types.DefaultTestDescriptor
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.normaliseLineSeparators

class TestExecutionResultEvaluatorTest extends Specification {

    def "evaluate throws exception if no results tracked"() {
        given:
        def ancestryTracker = Mock(BuildOperationAncestryTracker)
        def testExecutionRequest = Mock(TestExecutionRequestAction)
        TestExecutionResultEvaluator evaluator = new TestExecutionResultEvaluator(ancestryTracker, testExecutionRequest)
        1 * testExecutionRequest.getTaskSpecs() >> []

        def testDescriptorInternal = Mock(TestDescriptorInternal)
        def defaultTestDescriptor = Mock(DefaultTestDescriptor)
        1 * defaultTestDescriptor.getDisplayName() >> "Some Test Descriptor"
        1 * defaultTestDescriptor.getTaskPath() >> ":someTestTask"

        def testResult = Mock(TestResult)
        def testClassRequest = Mock(InternalJvmTestRequest)
        1 * testClassRequest.getClassName() >> "org.acme.SomeFooTest"
        1 * testClassRequest.getMethodName() >> null

        def testMethodRequest = Mock(InternalJvmTestRequest)
        1 * testMethodRequest.getClassName() >> "org.acme.SomeFooTest"
        1 * testMethodRequest.getMethodName() >> "fooMethod"

        def testMethodRequest2 = Mock(InternalJvmTestRequest)
        1 * testMethodRequest2.getClassName() >> "org.acme.SomeBazTest"
        1 * testMethodRequest2.getMethodName() >> "bazMethod"


        def details = Mock(ExecuteTestBuildOperationType.Details) {
            getTestDescriptor() >> testDescriptorInternal
        }

        def result = Mock(ExecuteTestBuildOperationType.Result) {
            getResult() >> testResult
        }

        def descriptor = BuildOperationDescriptor.displayName("foo")
            .details(details)
            .build()

        when:
        evaluator.finished(descriptor, new OperationFinishEvent(0, 1, null, result))
        evaluator.evaluate()

        then:
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.message) == """No matching tests found in any candidate test task.
    Requested tests:
        Some Test Descriptor (Task: ':someTestTask')
        Test class org.acme.SomeFooTest
        Test method org.acme.SomeFooTest.fooMethod()
        Test method org.acme.SomeBazTest.bazMethod()"""

        and:
        2 * testExecutionRequest.getTestExecutionDescriptors() >> [defaultTestDescriptor]
        1 * testExecutionRequest.getInternalJvmTestRequests() >> [testClassRequest, testMethodRequest, testMethodRequest2]
    }

    def "evaluate throws exception if test failed"() {
        given:
        def ancestryTracker = Mock(BuildOperationAncestryTracker)
        1 * ancestryTracker.findClosestExistingAncestor(_, _) >> Optional.of(":someproject:someTestTask")
        def testExecutionRequest = Mock(TestExecutionRequestAction)
        1 * testExecutionRequest.getInternalJvmTestRequests() >> []
        1 * testExecutionRequest.testExecutionDescriptors >> []
        1 * testExecutionRequest.taskSpecs >> []
        TestExecutionResultEvaluator evaluator = new TestExecutionResultEvaluator(ancestryTracker, testExecutionRequest)

        def testDescriptorInternal = Mock(TestDescriptorInternal)

        testDescriptorInternal.getName() >> "someTest"
        testDescriptorInternal.getClassName() >> "acme.SomeTestClass"

        def testResult = Mock(TestResult)
        1 * testResult.getTestCount() >> 1
        1 * testResult.getFailedTestCount() >> 1

        def testTask = Mock(TaskInternal)
        1 * testTask.getPath() >> ":someproject:someTestTask"
        def taskBuildOperation = BuildOperationDescriptor.displayName("task")
            .details(new ExecuteTaskBuildOperationDetails(Stub(LocalTaskNode) {
                getTask() >> testTask
            }))
            .build(new OperationIdentifier(1), null)

        def details = Mock(ExecuteTestBuildOperationType.Details) {
            getTestDescriptor() >> testDescriptorInternal
        }

        def result = Mock(ExecuteTestBuildOperationType.Result) {
            getResult() >> testResult
        }

        def testBuildOperation = BuildOperationDescriptor.displayName("foo")
            .details(details)
            .build()

        when:
        evaluator.started(taskBuildOperation, new OperationStartEvent(0))
        evaluator.finished(testBuildOperation, new OperationFinishEvent(0, 0, null, result))
        evaluator.evaluate()

        then:
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.message) == """Test failed.
    Failed tests:
        Test acme.SomeTestClass#someTest (Task: :someproject:someTestTask)"""
    }

}
