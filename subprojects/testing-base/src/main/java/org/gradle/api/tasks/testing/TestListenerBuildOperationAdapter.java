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

package org.gradle.api.tasks.testing;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.progress.BuildOperationCategory;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.internal.time.Clock;

import java.util.HashMap;
import java.util.Map;

/**
 * Emitting build operations for tests
 */
public class TestListenerBuildOperationAdapter implements TestListenerInternal {
    public static final TestOutputBuildOperationType.Details TEST_OUTPUT_DETAILS = new TestOutputBuildOperationType.Details() {
    };
    private final Map<TestDescriptor, TestBuildOperation> runningTests = new HashMap<TestDescriptor, TestBuildOperation>();
    private final BuildOperationState testRootBuildOperation;
    private final BuildOperationListener listener;
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final Clock clock;

    public TestListenerBuildOperationAdapter(BuildOperationState testRootBuildOperation, BuildOperationListener listener, BuildOperationIdFactory buildOperationIdFactory, Clock clock) {
        this.testRootBuildOperation = testRootBuildOperation;
        this.listener = listener;
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.clock = clock;
    }

    @Override
    public void started(final TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        if (testDescriptor.getParent() != null) {
            BuildOperationDescriptor testBuildOperationDescriptor = createTestBuildOperationDescriptor(testDescriptor, startEvent);
            long currentTime = clock.getCurrentTime();
            runningTests.put(testDescriptor, new TestBuildOperation(testBuildOperationDescriptor, currentTime));
            listener.started(testBuildOperationDescriptor, new OperationStartEvent(currentTime));
        }
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        if (testDescriptor.getParent() != null) {
            TestBuildOperation runningOp = runningTests.remove(testDescriptor);
            listener.finished(runningOp.descriptor, new OperationFinishEvent(runningOp.startTime, clock.getCurrentTime(), testResult.getException(), new BuildOperationTestResult(testResult)));
        }
    }

    @Override
    public void output(final TestDescriptorInternal testDescriptor, final TestOutputEvent event) {
        TestBuildOperation runningOp = runningTests.get(testDescriptor);
        BuildOperationDescriptor.Builder outputDescription = BuildOperationDescriptor.displayName(event.getDestination().name())
            .operationType(BuildOperationCategory.TASK)
            .details(TEST_OUTPUT_DETAILS);
        BuildOperationDescriptor outputBuildOperationDescriptor = outputDescription.build(newOperationIdentifier(), runningOp.descriptor.getId());
        long currentTime = clock.getCurrentTime();
        listener.started(outputBuildOperationDescriptor, new OperationStartEvent(currentTime));
        listener.finished(outputBuildOperationDescriptor, new OperationFinishEvent(currentTime, currentTime, null, new DefaultTestOutputBuildOperationResult(event)));
    }

    private BuildOperationDescriptor createTestBuildOperationDescriptor(TestDescriptor testDescriptor, TestStartEvent testStartEvent) {
        DefaultTestBuildOperationDetails details = new DefaultTestBuildOperationDetails(testDescriptor, testStartEvent.getStartTime());
        TestBuildOperation parentOperation = runningTests.get(testDescriptor.getParent());
        Object parentId = parentOperation == null ? testRootBuildOperation.getId() : parentOperation.descriptor.getId();
        return BuildOperationDescriptor.displayName(testDescriptor.getName())
            .operationType(BuildOperationCategory.TASK)
            .details(details)
            .build(newOperationIdentifier(), parentId);
    }

    private OperationIdentifier newOperationIdentifier() {
        return new OperationIdentifier(buildOperationIdFactory.nextId());
    }

    private class DefaultTestOutputBuildOperationResult implements TestOutputBuildOperationType.Result {
        private final TestOutputEvent event;

        public DefaultTestOutputBuildOperationResult(TestOutputEvent event) {
            this.event = event;
        }

        @Override
        public TestOutputEvent getOutput() {
            return event;
        }
    }

    private class DefaultTestBuildOperationDetails implements ExecuteTestBuildOperationType.Details {
        private final TestDescriptor testDescriptor;
        private long startTime;

        public DefaultTestBuildOperationDetails(TestDescriptor testDescriptor, long startTime) {
            this.testDescriptor = testDescriptor;
            this.startTime = startTime;
        }

        public TestDescriptor getTestDescriptor() {
            return testDescriptor;
        }

        public long getStartTime() {
            return startTime;
        }
    }

    private class TestBuildOperation {
        final BuildOperationDescriptor descriptor;
        final long startTime;

        public TestBuildOperation(BuildOperationDescriptor testBuildOperationDescriptor, long startTime) {
            this.descriptor = testBuildOperationDescriptor;
            this.startTime = startTime;
        }
    }
}
