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

import org.gradle.api.internal.artifacts.transform.ExecutePlannedTransformStepBuildOperationDetails;
import org.gradle.api.internal.artifacts.transform.TransformationNode;
import org.gradle.execution.plan.Node;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultTransformDescriptor;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;

/**
 * Transform listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 5.1
 */
class TransformOperationMapper implements BuildOperationMapper<ExecutePlannedTransformStepBuildOperationDetails, DefaultTransformDescriptor>, OperationDependencyLookup {
    private final Map<TransformationNode, DefaultTransformDescriptor> descriptors = new ConcurrentHashMap<>();
    private final OperationDependenciesResolver operationDependenciesResolver;

    TransformOperationMapper(OperationDependenciesResolver operationDependenciesResolver) {
        this.operationDependenciesResolver = operationDependenciesResolver;
    }

    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.TRANSFORM);
    }

    @Override
    public Class<ExecutePlannedTransformStepBuildOperationDetails> getDetailsType() {
        return ExecutePlannedTransformStepBuildOperationDetails.class;
    }

    @Override
    public InternalOperationDescriptor lookupExistingOperationDescriptor(Node node) {
        if (node instanceof TransformationNode) {
            return descriptors.get(node);
        }
        return null;
    }

    @Override
    public DefaultTransformDescriptor createDescriptor(ExecutePlannedTransformStepBuildOperationDetails details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        OperationIdentifier id = buildOperation.getId();
        String displayName = buildOperation.getDisplayName();
        String transformerName = details.getTransformerName();
        String subjectName = details.getSubjectName();
        Set<InternalOperationDescriptor> dependencies = operationDependenciesResolver.resolveDependencies(details.getTransformationNode());
        DefaultTransformDescriptor descriptor = new DefaultTransformDescriptor(id, displayName, parent, transformerName, subjectName, dependencies);
        descriptors.put(details.getTransformationNode(), descriptor);
        return descriptor;
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultTransformDescriptor descriptor, ExecutePlannedTransformStepBuildOperationDetails details, OperationStartEvent startEvent) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultTransformDescriptor descriptor, ExecutePlannedTransformStepBuildOperationDetails details, OperationFinishEvent finishEvent) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, toOperationResult(finishEvent));
    }
}
