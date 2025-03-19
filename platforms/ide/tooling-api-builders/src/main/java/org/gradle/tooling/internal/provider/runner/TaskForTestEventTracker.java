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
package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 */
class TaskForTestEventTracker implements BuildOperationTracker {
    private final BuildOperationAncestryTracker ancestryTracker;
    private final Map<Object, String> runningTasks = new ConcurrentHashMap<>();

    TaskForTestEventTracker(BuildOperationAncestryTracker ancestryTracker) {
        this.ancestryTracker = ancestryTracker;
    }

    /**
     * Returns the path for the test task that is an ancestor of the given build operation.
     */
    public String getTaskPath(OperationIdentifier buildOperationId) {
        return ancestryTracker.findClosestExistingAncestor(buildOperationId, runningTasks::get).get();
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        Object details = buildOperation.getDetails();
        if (details instanceof ExecuteTaskBuildOperationDetails) {
            TaskInternal task = ((ExecuteTaskBuildOperationDetails) details).getTask();
            String previous = runningTasks.put(buildOperation.getId(), task.getIdentityPath().getPath());
            if (previous != null) {
                throw new IllegalStateException("Build operation " + buildOperation.getId() + " already started.");
            }
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (buildOperation.getDetails() instanceof ExecuteTaskBuildOperationDetails) {
            runningTasks.remove(buildOperation.getId());
        }
    }
}
