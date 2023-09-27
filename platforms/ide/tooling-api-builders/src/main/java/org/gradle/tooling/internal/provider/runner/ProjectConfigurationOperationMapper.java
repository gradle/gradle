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

import com.google.common.collect.ImmutableList;
import org.gradle.configuration.project.ConfigureProjectBuildOperationType;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.build.event.types.AbstractProjectConfigurationResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultProjectConfigurationDescriptor;
import org.gradle.internal.build.event.types.DefaultProjectConfigurationFailureResult;
import org.gradle.internal.build.event.types.DefaultProjectConfigurationSuccessResult;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult;

import javax.annotation.Nullable;
import java.util.List;

import static java.util.Collections.singletonList;

class ProjectConfigurationOperationMapper implements BuildOperationMapper<ConfigureProjectBuildOperationType.Details, DefaultProjectConfigurationDescriptor> {
    private final ProjectConfigurationTracker projectConfigurationTracker;

    ProjectConfigurationOperationMapper(ProjectConfigurationTracker projectConfigurationTracker) {
        this.projectConfigurationTracker = projectConfigurationTracker;
    }

    @Override
    public boolean isEnabled(BuildEventSubscriptions subscriptions) {
        return subscriptions.isRequested(OperationType.PROJECT_CONFIGURATION);
    }

    @Override
    public Class<ConfigureProjectBuildOperationType.Details> getDetailsType() {
        return ConfigureProjectBuildOperationType.Details.class;
    }

    @Override
    public List<BuildOperationTracker> getTrackers() {
        return ImmutableList.of(projectConfigurationTracker);
    }

    @Override
    public DefaultProjectConfigurationDescriptor createDescriptor(ConfigureProjectBuildOperationType.Details details, BuildOperationDescriptor buildOperation, @Nullable OperationIdentifier parent) {
        OperationIdentifier id = buildOperation.getId();
        String displayName = buildOperation.getDisplayName();
        return new DefaultProjectConfigurationDescriptor(id, displayName, parent, details.getRootDir(), details.getProjectPath());
    }

    @Override
    public InternalOperationStartedProgressEvent createStartedEvent(DefaultProjectConfigurationDescriptor descriptor, ConfigureProjectBuildOperationType.Details details, OperationStartEvent startEvent) {
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor);
    }

    @Override
    public InternalOperationFinishedProgressEvent createFinishedEvent(DefaultProjectConfigurationDescriptor descriptor, ConfigureProjectBuildOperationType.Details details, OperationFinishEvent finishEvent) {
        AbstractProjectConfigurationResult result = toProjectConfigurationOperationResult(finishEvent, projectConfigurationTracker.resultsFor(descriptor.getId()));
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), descriptor, result);
    }

    private AbstractProjectConfigurationResult toProjectConfigurationOperationResult(OperationFinishEvent finishEvent, List<InternalPluginApplicationResult> pluginApplicationResults) {
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        Throwable failure = finishEvent.getFailure();
        if (failure != null) {
            return new DefaultProjectConfigurationFailureResult(startTime, endTime, singletonList(DefaultFailure.fromThrowable(failure)), pluginApplicationResults);
        }
        return new DefaultProjectConfigurationSuccessResult(startTime, endTime, pluginApplicationResults);
    }
}
