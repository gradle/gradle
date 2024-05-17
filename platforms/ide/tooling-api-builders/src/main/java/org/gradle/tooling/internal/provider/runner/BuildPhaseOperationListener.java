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

import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultBuildPhaseDescriptor;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFailureResult;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultSuccessResult;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

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

    public BuildPhaseOperationListener(ProgressEventConsumer eventConsumer, BuildOperationIdFactory idFactory) {
        this.eventConsumer = eventConsumer;
        this.idFactory = idFactory;
        this.descriptors = new ConcurrentHashMap<>();
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (!isSupportedBuildOperation(buildOperation)) {
            return;
        }
        DefaultBuildPhaseDescriptor descriptor = toBuildOperationDescriptor(buildOperation);
        descriptors.put(buildOperation.getId(), descriptor);
        eventConsumer.started(new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor));
    }

    private DefaultBuildPhaseDescriptor toBuildOperationDescriptor(BuildOperationDescriptor buildOperation) {
        OperationIdentifier operationId = new OperationIdentifier(idFactory.nextId());
        OperationIdentifier parent = eventConsumer.findStartedParentId(buildOperation);
        String name = buildOperation.getName();
        String displayName = "Build phase: " + buildOperation.getDisplayName();
        String buildPhase = buildOperation.getMetadata().toString();
        return new DefaultBuildPhaseDescriptor(operationId, name, displayName, parent, buildPhase, buildOperation.getTotalProgress());
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
            long endTime = finishEvent.getEndTime();
            AbstractOperationResult result = toOperationResult(finishEvent);
            eventConsumer.finished(new DefaultOperationFinishedProgressEvent(endTime, descriptor, result));
        }
    }

    private AbstractOperationResult toOperationResult(OperationFinishEvent finishEvent) {
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        if (finishEvent.getFailure() != null) {
            return new DefaultFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(finishEvent.getFailure())));
        } else {
            return new DefaultSuccessResult(startTime, endTime);
        }
    }

    private boolean isSupportedBuildOperation(BuildOperationDescriptor buildOperation) {
        return buildOperation.getMetadata() instanceof BuildOperationCategory && SUPPORTED_CATEGORIES.contains(buildOperation.getMetadata());
    }
}
