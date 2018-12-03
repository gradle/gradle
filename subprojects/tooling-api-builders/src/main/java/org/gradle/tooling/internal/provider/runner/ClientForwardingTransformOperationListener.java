/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationType;
import org.gradle.execution.plan.TransformationNodeIdentifier;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTransformDescriptor;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.DefaultOperationFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTransformDescriptor;
import org.gradle.util.Path;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toCollection;
import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;

/**
 * Transform listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 5.1
 */
class ClientForwardingTransformOperationListener extends SubtreeFilteringBuildOperationListener<ExecuteScheduledTransformationStepBuildOperationType.Details> implements TransformOperationTracker {

    private final Map<Long, DefaultTransformDescriptor> descriptors = new ConcurrentHashMap<>();
    private final TaskExecutionGraphTracker taskExecutionGraphTracker;

    ClientForwardingTransformOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, BuildOperationListener delegate, TaskExecutionGraphTracker taskExecutionGraphTracker) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.TRANSFORM, ExecuteScheduledTransformationStepBuildOperationType.Details.class);
        this.taskExecutionGraphTracker = taskExecutionGraphTracker;
    }

    @Override
    public InternalTransformDescriptor findOperationDescriptor(TransformationNodeIdentifier identifier) {
        return descriptors.get(identifier.getUniqueId());
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ExecuteScheduledTransformationStepBuildOperationType.Details details) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toTransformDescriptor(buildOperation, details));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ExecuteScheduledTransformationStepBuildOperationType.Details details) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), toTransformDescriptor(buildOperation, details), toOperationResult(finishEvent));
    }

    private DefaultTransformDescriptor toTransformDescriptor(BuildOperationDescriptor buildOperation, ExecuteScheduledTransformationStepBuildOperationType.Details details) {
        return descriptors.computeIfAbsent(details.getTransformationId(), uniqueId -> {
            OperationIdentifier id = buildOperation.getId();
            String displayName = buildOperation.getDisplayName();
            Object parentId = eventConsumer.findStartedParentId(buildOperation);
            String transformerName = details.getTransformerName();
            String subjectName = details.getSubjectName();
            return new DefaultTransformDescriptor(id, displayName, parentId, transformerName, subjectName, computeTransformDependencies(details));
        });
    }

    private Set<InternalTransformDescriptor> computeTransformDependencies(ExecuteScheduledTransformationStepBuildOperationType.Details details) {
        TaskExecutionGraphInternal taskExecutionGraph = taskExecutionGraphTracker.getTaskExecutionGraph(Path.path(details.getBuildPath()));
        DefaultTransformationNodeIdentifier transformationNodeIdentifier = new DefaultTransformationNodeIdentifier(details.getTransformationId());
        return taskExecutionGraph.getExecutionDependencies(transformationNodeIdentifier).getTransformations().stream()
            .map(TransformationNodeIdentifier::getUniqueId)
            .map(descriptors::get)
            .filter(Objects::nonNull)
            .collect(toCollection(LinkedHashSet::new));
    }

    private static class DefaultTransformationNodeIdentifier implements TransformationNodeIdentifier {

        private final long transformationId;

        public DefaultTransformationNodeIdentifier(long transformationId) {
            this.transformationId = transformationId;
        }

        @Override
        public long getUniqueId() {
            return transformationId;
        }

    }

}
