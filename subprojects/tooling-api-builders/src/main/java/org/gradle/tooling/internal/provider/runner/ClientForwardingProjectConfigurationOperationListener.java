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

import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType;
import org.gradle.configuration.project.ConfigureProjectBuildOperationType;
import org.gradle.internal.MutableLong;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginConfigurationResult;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractProjectConfigurationResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultOperationFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultOperationStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultPluginConfigurationResult;
import org.gradle.tooling.internal.provider.events.DefaultPluginIdentifier;
import org.gradle.tooling.internal.provider.events.DefaultProjectConfigurationDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultProjectConfigurationFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultProjectConfigurationSuccessResult;
import org.gradle.tooling.internal.provider.events.OperationResultPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;

/**
 * Project configuration listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingProjectConfigurationOperationListener extends SubtreeFilteringBuildOperationListener<ConfigureProjectBuildOperationType.Details> {

    private final Map<OperationIdentifier, ProjectConfigurationResult> results = new ConcurrentHashMap<>();

    ClientForwardingProjectConfigurationOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, BuildOperationListener delegate, OperationResultPostProcessor operationResultPostProcessor) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.PROJECT_CONFIGURATION, ConfigureProjectBuildOperationType.Details.class);
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (isEnabled() && buildOperation.getParentId() != null && results.containsKey(buildOperation.getParentId())) {
            results.put(buildOperation.getId(), results.get(buildOperation.getParentId()));
        }
        super.started(buildOperation, startEvent);
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        super.finished(buildOperation, finishEvent);
        if (isEnabled() && results.containsKey(buildOperation.getId())) {
            if (buildOperation.getDetails() instanceof ApplyPluginBuildOperationType.Details) {
                ApplyPluginBuildOperationType.Details details = (ApplyPluginBuildOperationType.Details) buildOperation.getDetails();
                DefaultPluginIdentifier plugin = new DefaultPluginIdentifier(details.getPluginClass(), details.getPluginId());
                long duration = finishEvent.getEndTime() - finishEvent.getStartTime();
                results.get(buildOperation.getId()).add(plugin, duration);
            }
            results.remove(buildOperation.getId());
        }
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ConfigureProjectBuildOperationType.Details details) {
        results.put(buildOperation.getId(), new ProjectConfigurationResult());
        return new DefaultOperationStartedProgressEvent(startEvent.getStartTime(), toProjectConfigurationDescriptor(buildOperation, details));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ConfigureProjectBuildOperationType.Details details) {
        AbstractProjectConfigurationResult result = toProjectConfigurationOperationResult(finishEvent, results.remove(buildOperation.getId()));
        return new DefaultOperationFinishedProgressEvent(finishEvent.getEndTime(), toProjectConfigurationDescriptor(buildOperation, details), result);
    }

    private DefaultProjectConfigurationDescriptor toProjectConfigurationDescriptor(BuildOperationDescriptor buildOperation, ConfigureProjectBuildOperationType.Details details) {
        Object id = buildOperation.getId();
        String displayName = buildOperation.getDisplayName();
        Object parentId = eventConsumer.findStartedParentId(buildOperation.getParentId());
        return new DefaultProjectConfigurationDescriptor(id, displayName, parentId, details.getRootDir(), details.getProjectPath());
    }

    private AbstractProjectConfigurationResult toProjectConfigurationOperationResult(OperationFinishEvent finishEvent, ProjectConfigurationResult configResult) {
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        Throwable failure = finishEvent.getFailure();
        List<InternalPluginConfigurationResult> pluginConfigurationResults = configResult.toInternalPluginConfigurationResults();
        if (failure != null) {
            return new DefaultProjectConfigurationFailureResult(startTime, endTime, singletonList(DefaultFailure.fromThrowable(failure)), pluginConfigurationResults);
        }
        return new DefaultProjectConfigurationSuccessResult(startTime, endTime, pluginConfigurationResults);
    }

    private static class ProjectConfigurationResult {

        private final Map<DefaultPluginIdentifier, MutableLong> durations = new LinkedHashMap<>();

        public void add(DefaultPluginIdentifier plugin, long duration) {
            durations.computeIfAbsent(plugin, key -> new MutableLong()).increment(duration);
        }

        public List<InternalPluginConfigurationResult> toInternalPluginConfigurationResults() {
            return durations.entrySet().stream()
                .map(entry -> new DefaultPluginConfigurationResult(entry.getKey(), Duration.ofMillis(entry.getValue().get())))
                .collect(toCollection(ArrayList::new));
        }

    }

}
