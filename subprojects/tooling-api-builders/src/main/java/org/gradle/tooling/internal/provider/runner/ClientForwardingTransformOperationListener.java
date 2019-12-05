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

import org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationDetails;
import org.gradle.api.internal.artifacts.transform.TransformationNode;
import org.gradle.execution.plan.Node;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTransformDescriptor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;

/**
 * Transform listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 5.1
 */
class ClientForwardingTransformOperationListener extends SubtreeFilteringBuildOperationListener<ExecuteScheduledTransformationStepBuildOperationDetails> implements OperationDependencyLookup {

    private final Map<TransformationNode, DefaultTransformDescriptor> descriptors = new ConcurrentHashMap<>();
    private final OperationDependenciesResolver operationDependenciesResolver;

    ClientForwardingTransformOperationListener(ProgressEventConsumer eventConsumer, BuildEventSubscriptions clientSubscriptions, BuildOperationListener delegate,
                                               OperationDependenciesResolver operationDependenciesResolver) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.TRANSFORM, ExecuteScheduledTransformationStepBuildOperationDetails.class);
        this.operationDependenciesResolver = operationDependenciesResolver;
    }

    @Override
    public InternalOperationDescriptor lookupExistingOperationDescriptor(Node node) {
        if (isEnabled() && node instanceof TransformationNode) {
            return descriptors.get(node);
        }
        return null;
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ExecuteScheduledTransformationStepBuildOperationDetails details) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toTransformDescriptor(buildOperation, details));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ExecuteScheduledTransformationStepBuildOperationDetails details) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), toTransformDescriptor(buildOperation, details), toOperationResult(finishEvent));
    }

    private DefaultTransformDescriptor toTransformDescriptor(BuildOperationDescriptor buildOperation, ExecuteScheduledTransformationStepBuildOperationDetails details) {
        return descriptors.computeIfAbsent(details.getTransformationNode(), transformationNode -> {
            OperationIdentifier id = buildOperation.getId();
            String displayName = buildOperation.getDisplayName();
            Object parentId = eventConsumer.findStartedParentId(buildOperation);
            String transformerName = details.getTransformerName();
            String subjectName = details.getSubjectName();
            Set<InternalOperationDescriptor> dependencies = operationDependenciesResolver.resolveDependencies(transformationNode);
            return new DefaultTransformDescriptor(id, displayName, parentId, transformerName, subjectName, dependencies);
        });
    }

}
