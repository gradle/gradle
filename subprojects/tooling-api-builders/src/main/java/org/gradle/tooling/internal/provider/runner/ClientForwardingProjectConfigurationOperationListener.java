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

import org.gradle.configuration.project.ConfigureProjectBuildOperationType;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.DefaultOperationFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultProjectConfigurationDescriptor;
import org.gradle.tooling.internal.provider.events.OperationResultPostProcessor;

import static org.gradle.tooling.internal.provider.runner.ClientForwardingBuildOperationListener.toOperationResult;

/**
 * Project configuration listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingProjectConfigurationOperationListener extends SubtreeFilteringBuildOperationListener<ConfigureProjectBuildOperationType.Details> {

    ClientForwardingProjectConfigurationOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, BuildOperationListener delegate, OperationResultPostProcessor operationResultPostProcessor) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.PROJECT_CONFIGURATION, ConfigureProjectBuildOperationType.Details.class);
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ConfigureProjectBuildOperationType.Details details) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toProjectConfigurationDescriptor(buildOperation, details));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ConfigureProjectBuildOperationType.Details details) {
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), toProjectConfigurationDescriptor(buildOperation, details), toOperationResult(finishEvent));
    }

    private DefaultProjectConfigurationDescriptor toProjectConfigurationDescriptor(BuildOperationDescriptor buildOperation, ConfigureProjectBuildOperationType.Details details) {
        Object id = buildOperation.getId();
        String displayName = buildOperation.getDisplayName();
        Object parentId = eventConsumer.findStartedParentId(buildOperation.getParentId());
        return new DefaultProjectConfigurationDescriptor(id, displayName, parentId, details.getRootDir(), details.getProjectPath());
    }

}
