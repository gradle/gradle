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
import org.gradle.internal.operations.BuildOperationExecHandle;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;

import java.util.HashMap;
import java.util.Map;

/**
 * Emitting build operations for tests
 */
public class TestListenerBuildOperationAdapter implements TestListenerInternal {
    private final BuildOperationExecutor buildOperationExecutor;
    private final Map<TestDescriptorInternal, BuildOperationExecHandle> runningTests = new HashMap<TestDescriptorInternal, BuildOperationExecHandle>();
    private final BuildOperationState parentOperationState;

    public TestListenerBuildOperationAdapter(BuildOperationState parentOperationState, BuildOperationExecutor buildOperationExecutor) {
        this.parentOperationState = parentOperationState;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public synchronized void started(final TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        System.out.println("testDescriptor.getName() started = " + testDescriptor.getName());
        if (testDescriptor.getParent() != null) {
            BuildOperationExecHandle parentOperationExecHandle = runningTests.get(testDescriptor.getParent());
            BuildOperationDescriptor.Builder description = BuildOperationDescriptor.displayName(testDescriptor.getName()).details(new TestBuildOperationType.Details() {
                @Override
                public String getClassName() {
                    return testDescriptor.getClassName();
                }

                @Override
                public String getName() {
                    return testDescriptor.getName();
                }

                @Override
                public boolean isComposite() {
                    return testDescriptor.isComposite();
                }

                @Override
                public int hashCode() {
                    return super.hashCode();
                }
            });

            if (parentOperationExecHandle == null) {
                description.parent(parentOperationState);
                BuildOperationExecHandle handle = buildOperationExecutor.start(description);
                runningTests.put(testDescriptor, handle);
            } else {
                BuildOperationExecHandle handle = parentOperationExecHandle.startChild(description);
                runningTests.put(testDescriptor, handle);
            }
        }
    }

    @Override
    public synchronized void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        System.out.println("testDescriptor.getName() completed = " + testDescriptor.getName());
        if (testDescriptor.getParent() != null) {
            BuildOperationExecHandle buildOperationExecHandle = runningTests.remove(testDescriptor);
            buildOperationExecHandle.finish(new BuildOperationTestResult(testResult));
        }
    }

    @Override
    public void output(final TestDescriptorInternal testDescriptor, final TestOutputEvent event) {
        System.out.println("testDescriptor output = " + testDescriptor.getName());
        if (testDescriptor.getParent() != null) {
            BuildOperationExecHandle buildOperationExecHandle = runningTests.get(testDescriptor);
            BuildOperationDescriptor.Builder description = BuildOperationDescriptor.displayName(testDescriptor.getName() + "--" + event.getDestination()).details(new TestOutputBuildOperationType.Details() {
            });
            buildOperationExecHandle.emitChildBuildOperation(description, new DefaultTestOutputBuildOperationResult(event), null);

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
}
