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
    private final Map<TestDescriptorInternal, TestOperationDescriptor> runningTests = new HashMap<TestDescriptorInternal, TestOperationDescriptor>();
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
            TestOperationDescriptor testOperationDescriptor = createTestBuildOperation(testDescriptor, startEvent);
            runningTests.put(testDescriptor, testOperationDescriptor);
            listener.started(testOperationDescriptor.buildOperationDescriptor, new OperationStartEvent(startEvent.getStartTime()));
        }
    }

    private TestOperationDescriptor createTestBuildOperation(final TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        BuildOperationDescriptor.Builder description = BuildOperationDescriptor.displayName(testDescriptor.getName()).details(new DefaultTestBuildOperationDetails(testDescriptor.getClassName(), testDescriptor.getName(), testDescriptor.isComposite()));
        TestOperationDescriptor parentTestBuildOperationDescriptor = runningTests.get(testDescriptor.getParent());
        Object parentId = parentTestBuildOperationDescriptor == null ? testRootBuildOperation.getId() : parentTestBuildOperationDescriptor.buildOperationDescriptor.getId();
        BuildOperationDescriptor descriptor = createDescriptor(BuildOperationDescriptor.displayName(testDescriptor.getName()).details(description),
            parentId);
        return new TestOperationDescriptor(descriptor, startEvent);
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        if (testDescriptor.getParent() != null) {
            TestOperationDescriptor testBuildOperationDescriptor = runningTests.remove(testDescriptor);
            listener.finished(testBuildOperationDescriptor.buildOperationDescriptor, new OperationFinishEvent(testBuildOperationDescriptor.startEvent.getStartTime(), completeEvent.getEndTime(), testResult.getException(), new BuildOperationTestResult(testResult)));
        }
    }

    @Override
    public void output(final TestDescriptorInternal testDescriptor, final TestOutputEvent event) {
        if (testDescriptor.getParent() != null) {
            TestOperationDescriptor testBuildOperationDescriptor = runningTests.get(testDescriptor);
            BuildOperationDescriptor.Builder description = BuildOperationDescriptor.displayName(testDescriptor.getName() + "--" + event.getDestination()).details(TEST_OUTPUT_DETAILS);
            long currentTime = clock.getCurrentTime();
            BuildOperationDescriptor descriptor = createDescriptor(description, testBuildOperationDescriptor.buildOperationDescriptor.getId());
            listener.started(descriptor, new OperationStartEvent(currentTime));
            listener.finished(descriptor, new OperationFinishEvent(currentTime, currentTime, null, new DefaultTestOutputBuildOperationResult(event)));
        }
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

    private BuildOperationDescriptor createDescriptor(BuildOperationDescriptor.Builder descriptorBuilder, Object parentIdentifier) {
        OperationIdentifier id = new OperationIdentifier(buildOperationIdFactory.nextId());
        return descriptorBuilder.build(id, parentIdentifier);
    }

    private class TestOperationDescriptor {
        public final BuildOperationDescriptor buildOperationDescriptor;
        public final TestStartEvent startEvent;

        public TestOperationDescriptor(BuildOperationDescriptor buildOperationDescriptor, TestStartEvent startEvent) {
            this.buildOperationDescriptor = buildOperationDescriptor;
            this.startEvent = startEvent;
        }
    }

    private class DefaultTestBuildOperationDetails implements TestBuildOperationType.Details {
        private final String className;
        private final String name;
        private final boolean composite;

        public DefaultTestBuildOperationDetails(String className, String name, boolean composite) {
            this.className = className;
            this.name = name;
            this.composite = composite;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isComposite() {
            return composite;
        }
    }
}
