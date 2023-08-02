/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.operations.UncategorizedBuildOperations;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

public class DefaultWorkExecutionTracker implements WorkExecutionTracker, Closeable {

    private final BuildOperationAncestryTracker buildOperationAncestryTracker;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final OperationListener operationListener = new OperationListener();

    public DefaultWorkExecutionTracker(
        BuildOperationAncestryTracker buildOperationAncestryTracker,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.buildOperationAncestryTracker = buildOperationAncestryTracker;
        this.buildOperationListenerManager = buildOperationListenerManager;
        buildOperationListenerManager.addListener(operationListener);
    }

    @Override
    public Optional<TaskInternal> getCurrentTask() {
        return buildOperationAncestryTracker
            .findClosestExistingAncestor(
                currentBuildOperationRef.getId(),
                operationListener.runningTasks::get
            );
    }

    @Override
    public boolean isExecutingTransformAction() {
        return buildOperationAncestryTracker.findClosestMatchingAncestor(
            currentBuildOperationRef.getId(), operationListener.runningTransformActions::contains
        ).isPresent();
    }

    @Override
    public void close() throws IOException {
        buildOperationListenerManager.removeListener(operationListener);
        assert !operationListener.hasRunningWork();
    }

    private static class OperationListener implements BuildOperationListener {

        final Map<OperationIdentifier, TaskInternal> runningTasks = new ConcurrentHashMap<>();
        final Set<OperationIdentifier> runningTransformActions = ConcurrentHashMap.newKeySet();

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            if (isTransformAction(buildOperation)) {
                runningTransformActions.add(mandatoryIdOf(buildOperation));
            } else {
                Object details = buildOperation.getDetails();
                if (details instanceof ExecuteTaskBuildOperationDetails) {
                    runningTasks.put(mandatoryIdOf(buildOperation), ((ExecuteTaskBuildOperationDetails) details).getTask());
                }
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            if (isTransformAction(buildOperation)) {
                runningTransformActions.remove(mandatoryIdOf(buildOperation));
            } else {
                Object details = buildOperation.getDetails();
                if (details instanceof ExecuteTaskBuildOperationType.Details) {
                    Object removed = runningTasks.remove(mandatoryIdOf(buildOperation));
                    if (removed == null) {
                        throw new IllegalStateException(format("Task build operation %s was finished without being started.", buildOperation));
                    }
                }
            }
        }

        private OperationIdentifier mandatoryIdOf(BuildOperationDescriptor buildOperation) {
            OperationIdentifier id = buildOperation.getId();
            if (id == null) {
                throw new IllegalStateException(format("Build operation %s has no valid id", buildOperation));
            }
            return id;
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        }

        public boolean hasRunningWork() {
            return !runningTasks.isEmpty() || !runningTransformActions.isEmpty();
        }

        private static boolean isTransformAction(BuildOperationDescriptor buildOperation) {
            return UncategorizedBuildOperations.TRANSFORM_ACTION.equals(buildOperation.getMetadata());
        }
    }
}
