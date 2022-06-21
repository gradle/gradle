/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.internal.build.event.types.DefaultBuildPhaseDescriptor;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.internal.operations.BuildOperationCategory.CONFIGURE_BUILD;
import static org.gradle.internal.operations.BuildOperationCategory.CONFIGURE_ROOT_BUILD;
import static org.gradle.internal.operations.BuildOperationCategory.RUN_MAIN_TASKS;
import static org.gradle.internal.operations.BuildOperationCategory.RUN_WORK;

public class BuildPhaseOperationListener implements BuildOperationListener {

    private static final Set<BuildOperationCategory> SUPPORTED_CATEGORIES = Collections.unmodifiableSet(EnumSet.of(
        CONFIGURE_ROOT_BUILD,
        CONFIGURE_BUILD,
        RUN_MAIN_TASKS,
        RUN_WORK
    ));

    private final ProgressEventConsumer eventConsumer;
    private final BuildOperationIdFactory idFactory;

    private final Map<OperationIdentifier, DefaultBuildPhaseDescriptor> descriptors;
    private final BuildPhaseOperationMapper operationMapper;

    public BuildPhaseOperationListener(ProgressEventConsumer eventConsumer, BuildOperationIdFactory idFactory) {
        this.eventConsumer = eventConsumer;
        this.idFactory = idFactory;
        this.operationMapper = new BuildPhaseOperationMapper();
        this.descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (!isSupportedBuildOperation(buildOperation)) {
            return;
        }
        OperationIdentifier operationId = new OperationIdentifier(idFactory.nextId());
        OperationIdentifier parentId = eventConsumer.findStartedParentId(buildOperation);
        BuildOperationDescriptor newBuildOperation = BuildOperationDescriptor.displayName("Build phase: " + buildOperation.getDisplayName())
            .metadata(buildOperation.getMetadata())
            .name(buildOperation.getName())
            .totalProgress(buildOperation.getTotalProgress())
            .build(operationId, parentId);
        DefaultBuildPhaseDescriptor descriptor = operationMapper.createDescriptor(null, newBuildOperation, parentId);
        descriptors.put(buildOperation.getId(), descriptor);
        InternalOperationStartedProgressEvent startedEvent = operationMapper.createStartedEvent(descriptor, null, startEvent);
        eventConsumer.started(startedEvent);
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        if (!isSupportedBuildOperation(buildOperation)) {
            return;
        }
        DefaultBuildPhaseDescriptor descriptor = descriptors.remove(buildOperation.getId());
        if (descriptor != null) {
            InternalOperationFinishedProgressEvent finishedEvent = operationMapper.createFinishedEvent(descriptor, null, finishEvent);
            eventConsumer.finished(finishedEvent);
        }
    }

    private boolean isSupportedBuildOperation(BuildOperationDescriptor buildOperation) {
        return buildOperation.getMetadata() instanceof BuildOperationCategory && SUPPORTED_CATEGORIES.contains(buildOperation.getMetadata());
    }
}
