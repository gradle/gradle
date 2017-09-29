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

import java.util.HashMap;
import java.util.Map;

public class TestListenerBuildOperationAdapter implements TestListenerInternal {
    private final BuildOperationExecutor buildOperationExecutor;
    private final Map<TestDescriptorInternal, BuildOperationExecHandle> runningTests = new HashMap<TestDescriptorInternal, BuildOperationExecHandle>();

    public TestListenerBuildOperationAdapter(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public synchronized void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        if (testDescriptor.getParent() != null) {
            System.out.println("TestListenerBuildOperationAdapter.started");
            System.out.println("testDescriptor.getName() = " + testDescriptor.getName());

            BuildOperationExecHandle parentOperationExecHandle = runningTests.get(testDescriptor.getParent());
            BuildOperationDescriptor.Builder description = BuildOperationDescriptor.displayName(testDescriptor.getName());

            if (parentOperationExecHandle == null) {
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
        if (testDescriptor.getParent() != null) {
            System.out.println("TestListenerBuildOperationAdapter.completed");
            System.out.println("testDescriptor.getName() = " + testDescriptor.getName());

            BuildOperationExecHandle buildOperationExecHandle = runningTests.remove(testDescriptor);
            buildOperationExecHandle.finish(new BuildOperationTestResult(testDescriptor));
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        if (testDescriptor.getParent() != null) {
//            BuildOperationExecHandle buildOperationExecHandle = runningTests.remove(testDescriptor);
//            buildOperationExecHandle.finish(new BuildOperationTestResult(testDescriptor));
        }
    }
}
