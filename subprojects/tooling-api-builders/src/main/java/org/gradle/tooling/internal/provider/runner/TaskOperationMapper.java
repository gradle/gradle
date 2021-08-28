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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.TaskNode;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.OperationResultPostProcessor;
import org.gradle.internal.build.event.types.AbstractTaskResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultTaskDescriptor;
import org.gradle.internal.build.event.types.DefaultTaskFailureResult;
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTaskSkippedResult;
import org.gradle.internal.build.event.types.DefaultTaskStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTaskSuccessResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonList;

class TaskOperationMapper implements BuildOperationMapper<ExecuteTaskBuildOperationDetails, DefaultTaskDescriptor>, OperationDependencyLookup {
    private final Map<TaskIdentity<?>, DefaultTaskDescriptor> descriptors = new ConcurrentHashMap<>();
    private final OperationResultPostProcessor operationResultPostProcessor;
    private final TaskOriginTracker taskOriginTracker;
    private final OperationDependenciesResolver operationDependenciesResolver;

    TaskOperationMapper(OperationResultPostProcessor operationResultPostProcessor, TaskOriginTracker taskOriginTracker, OperationDependenciesResolver operationDependenciesResolver) {
        this.operationResultPostProcessor = operationResultPostProcessor;
        this.taskOriginTracker = taskOriginTracker;
        this.operationDependenciesResolver = operationDependenciesResolver;
    }

    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.TASK);
    }

    @Override
    public Class<ExecuteTaskBuildOperationDetails> getDetailsType() {
        return ExecuteTaskBuildOperationDetails.class;
    }

    @Override
    public List<? extends BuildOperationTracker> getTrackers() {
        return ImmutableList.of(taskOriginTracker);
    }

    @Override
    public InternalOperationDescriptor lookupExistingOperationDescriptor(Node node) {
        if (node instanceof TaskNode) {
            TaskNode taskNode = (TaskNode) node;
            return descriptors.get(taskNode.getTask().getTaskIdentity());
        }
        return null;
    }

    @Override
    public DefaultTaskDescriptor createDescriptor(ExecuteTaskBuildOperationDetails details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        OperationIdentifier id = buildOperation.getId();
        String taskIdentityPath = buildOperation.getName();
        String displayName = buildOperation.getDisplayName();
        String taskPath = details.getTask().getIdentityPath().getPath();
        Set<InternalOperationDescriptor> dependencies = operationDependenciesResolver.resolveDependencies(details.getTaskNode());
        InternalPluginIdentifier originPlugin = taskOriginTracker.getOriginPlugin(details.getTask().getTaskIdentity());
        DefaultTaskDescriptor descriptor = new DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parent, dependencies, originPlugin);
        descriptors.put(details.getTask().getTaskIdentity(), descriptor);
        return descriptor;
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultTaskDescriptor descriptor, ExecuteTaskBuildOperationDetails details, OperationStartEvent startEvent) {
        return new DefaultTaskStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultTaskDescriptor descriptor, ExecuteTaskBuildOperationDetails details, OperationFinishEvent finishEvent) {
        TaskInternal task = details.getTask();
        AbstractTaskResult taskResult = operationResultPostProcessor.process(toTaskResult(task, finishEvent), descriptor.getId());
        return new DefaultTaskFinishedProgressEvent(finishEvent.getEndTime(), descriptor, taskResult);
    }

    private static AbstractTaskResult toTaskResult(TaskInternal task, OperationFinishEvent finishEvent) {
        TaskStateInternal state = task.getState();
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        ExecuteTaskBuildOperationType.Result result = (ExecuteTaskBuildOperationType.Result) finishEvent.getResult();
        boolean incremental = result != null && result.isIncremental();

        if (state.getUpToDate()) {
            return new DefaultTaskSuccessResult(startTime, endTime, true, state.isFromCache(), state.getSkipMessage(), incremental, Collections.emptyList());
        } else if (state.getSkipped()) {
            return new DefaultTaskSkippedResult(startTime, endTime, state.getSkipMessage(), incremental);
        } else {
            List<String> executionReasons = result != null ? result.getUpToDateMessages() : null;
            Throwable failure = finishEvent.getFailure();
            if (failure == null) {
                return new DefaultTaskSuccessResult(startTime, endTime, false, state.isFromCache(), "SUCCESS", incremental, executionReasons);
            } else {
                return new DefaultTaskFailureResult(startTime, endTime, singletonList(DefaultFailure.fromThrowable(failure)), incremental, executionReasons);
            }
        }
    }

}
