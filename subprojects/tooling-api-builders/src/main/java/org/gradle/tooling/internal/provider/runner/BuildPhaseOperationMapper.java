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

import org.gradle.execution.BuildPhaseBuildOperationType;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractOperationResult;
import org.gradle.internal.build.event.types.DefaultBuildPhaseDescriptor;
import org.gradle.internal.build.event.types.DefaultFailureResult;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultSuccessResult;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;

import javax.annotation.Nullable;
import java.util.Collections;

public class BuildPhaseOperationMapper implements BuildOperationMapper<BuildPhaseBuildOperationType.Details, DefaultBuildPhaseDescriptor> {
    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.BUILD_PHASE);
    }

    @Override
    public Class<BuildPhaseBuildOperationType.Details> getDetailsType() {
        return BuildPhaseBuildOperationType.Details.class;
    }

    @Override
    public DefaultBuildPhaseDescriptor createDescriptor(BuildPhaseBuildOperationType.Details details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        if (!(buildOperation.getMetadata() instanceof BuildOperationCategory)) {
            throw new IllegalStateException("Build operation category: " + buildOperation.getMetadata() + " is not supported by " + this.getClass().getName() + ".");
        }
        switch ((BuildOperationCategory) buildOperation.getMetadata()) {
            case CONFIGURE_ROOT_BUILD:
            case CONFIGURE_BUILD:
            case RUN_MAIN_TASKS:
            case RUN_WORK:
                String buildPhase = buildOperation.getMetadata().toString();
                return new DefaultBuildPhaseDescriptor(buildOperation, parent, buildPhase, buildOperation.getTotalProgress());
            default:
                throw new IllegalStateException("Build operation category: " + buildOperation.getMetadata() + " is not supported by " + this.getClass().getName() + ".");
        }
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultBuildPhaseDescriptor descriptor, BuildPhaseBuildOperationType.Details details, OperationStartEvent startEvent) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultBuildPhaseDescriptor descriptor, BuildPhaseBuildOperationType.Details details, OperationFinishEvent finishEvent) {
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        AbstractOperationResult result;
        if (finishEvent.getFailure() != null) {
            // Don't report failure exception, since we anyway report failure in other events
            result = new DefaultFailureResult(startTime, endTime, Collections.emptyList());
        } else {
            result = new DefaultSuccessResult(startTime, endTime);
        }
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, result);
    }
}
