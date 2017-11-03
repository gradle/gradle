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

package org.gradle.api.internal.tasks.testing.operations;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationIdentifierRegistry;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.internal.time.Clock;

import java.util.HashMap;
import java.util.Map;

/**
 * Emits build operations representing test execution.
 *
 * A single instance is used per build, so may be adapting concurrent test executions.
 * However, this implementation is not thread safe,
 * but is relying on serialisation guarantees provided by ListenerManager.
 */
public class TestListenerBuildOperationAdapter implements TestListenerInternal {

    private static final TestOutputBuildOperationType.Details TEST_OUTPUT_DETAILS = new TestOutputBuildOperationType.Details() {
    };

    private final Map<TestDescriptor, InProgressExecuteTestBuildOperation> runningTests = new HashMap<TestDescriptor, InProgressExecuteTestBuildOperation>();
    private final BuildOperationListener listener;
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final Clock clock;

    public TestListenerBuildOperationAdapter(BuildOperationListener listener, BuildOperationIdFactory buildOperationIdFactory, Clock clock) {
        this.listener = listener;
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.clock = clock;
    }

    @Override
    public void started(final TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        long currentTime = clock.getCurrentTime();
        BuildOperationDescriptor testBuildOperationDescriptor = createTestBuildOperationDescriptor(testDescriptor, startEvent);
        runningTests.put(testDescriptor, new InProgressExecuteTestBuildOperation(testBuildOperationDescriptor, currentTime));
        listener.started(testBuildOperationDescriptor, new OperationStartEvent(currentTime));
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        long currentTime = clock.getCurrentTime();
        InProgressExecuteTestBuildOperation runningOp = runningTests.remove(testDescriptor);
        listener.finished(runningOp.descriptor, new OperationFinishEvent(runningOp.startTime, currentTime, testResult.getException(), new BuildOperationTestResult(testResult)));
    }

    @Override
    public void output(final TestDescriptorInternal testDescriptor, final TestOutputEvent event) {
        long currentTime = clock.getCurrentTime();
        InProgressExecuteTestBuildOperation runningOp = runningTests.get(testDescriptor);
        BuildOperationDescriptor.Builder outputDescription = BuildOperationDescriptor.displayName(event.getDestination().name())
            .details(TEST_OUTPUT_DETAILS);
        BuildOperationDescriptor outputBuildOperationDescriptor = outputDescription.build(newOperationIdentifier(), runningOp.descriptor.getId());
        listener.started(outputBuildOperationDescriptor, new OperationStartEvent(currentTime));
        listener.finished(outputBuildOperationDescriptor, new OperationFinishEvent(currentTime, currentTime, null, new DefaultTestOutputBuildOperationResult(event)));
    }

    private BuildOperationDescriptor createTestBuildOperationDescriptor(TestDescriptor testDescriptor, TestStartEvent testStartEvent) {
        DefaultTestBuildOperationDetails details = new DefaultTestBuildOperationDetails(testDescriptor, testStartEvent.getStartTime());
        InProgressExecuteTestBuildOperation parentOperation = runningTests.get(testDescriptor.getParent());
        Object parentId = parentOperation == null ? BuildOperationIdentifierRegistry.getCurrentOperationIdentifier() : parentOperation.descriptor.getId();
        return BuildOperationDescriptor.displayName(testDescriptor.getName())
            .details(details)
            .build(newOperationIdentifier(), parentId);
    }

    private OperationIdentifier newOperationIdentifier() {
        return new OperationIdentifier(buildOperationIdFactory.nextId());
    }

    private static class DefaultTestOutputBuildOperationResult implements TestOutputBuildOperationType.Result {
        private final TestOutputEvent event;

        DefaultTestOutputBuildOperationResult(TestOutputEvent event) {
            this.event = event;
        }

        @Override
        public TestOutputEvent getOutput() {
            return event;
        }
    }

    private static class DefaultTestBuildOperationDetails implements ExecuteTestBuildOperationType.Details {
        private final TestDescriptor testDescriptor;
        private long startTime;

        DefaultTestBuildOperationDetails(TestDescriptor testDescriptor, long startTime) {
            this.testDescriptor = testDescriptor;
            this.startTime = startTime;
        }

        @Override
        public TestDescriptor getTestDescriptor() {
            return testDescriptor;
        }

        @Override
        public long getStartTime() {
            return startTime;
        }
    }

    private static class BuildOperationTestResult implements ExecuteTestBuildOperationType.Result {
        final TestResult result;

        BuildOperationTestResult(TestResult testResult) {
            this.result = testResult;
        }

        @Override
        public TestResult getResult() {
            return result;
        }
    }

    private static class InProgressExecuteTestBuildOperation {
        final BuildOperationDescriptor descriptor;
        final long startTime;

        InProgressExecuteTestBuildOperation(BuildOperationDescriptor testBuildOperationDescriptor, long startTime) {
            this.descriptor = testBuildOperationDescriptor;
            this.startTime = startTime;
        }
    }
}
