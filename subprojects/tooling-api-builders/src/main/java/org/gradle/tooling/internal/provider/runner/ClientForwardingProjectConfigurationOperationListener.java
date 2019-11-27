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
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult;
import org.gradle.internal.build.event.types.AbstractProjectConfigurationResult;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultPluginApplicationResult;
import org.gradle.internal.build.event.types.DefaultProjectConfigurationDescriptor;
import org.gradle.internal.build.event.types.DefaultProjectConfigurationFailureResult;
import org.gradle.internal.build.event.types.DefaultProjectConfigurationSuccessResult;
import org.gradle.tooling.internal.provider.runner.PluginApplicationTracker.PluginApplication;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;

/**
 * Project configuration listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingProjectConfigurationOperationListener extends SubtreeFilteringBuildOperationListener<ConfigureProjectBuildOperationType.Details> {

    private final Map<OperationIdentifier, ProjectConfigurationResult> results = new ConcurrentHashMap<>();
    private final BuildOperationParentTracker parentTracker;
    private final PluginApplicationTracker pluginApplicationTracker;

    ClientForwardingProjectConfigurationOperationListener(ProgressEventConsumer eventConsumer, BuildEventSubscriptions clientSubscriptions, BuildOperationListener delegate,
                                                          BuildOperationParentTracker parentTracker, PluginApplicationTracker pluginApplicationTracker) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.PROJECT_CONFIGURATION, ConfigureProjectBuildOperationType.Details.class);
        this.parentTracker = parentTracker;
        this.pluginApplicationTracker = pluginApplicationTracker;
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        super.finished(buildOperation, finishEvent);
        if (isEnabled()) {
            PluginApplication pluginApplication = pluginApplicationTracker.getRunningPluginApplication(buildOperation.getId());
            if (pluginApplication != null) {
                ProjectConfigurationResult result = parentTracker.findClosestExistingAncestor(buildOperation.getParentId(), results::get);
                if (result != null && hasNoEnclosingRunningPluginApplicationForSamePlugin(buildOperation, pluginApplication.getPlugin())) {
                    result.increment(pluginApplication, finishEvent.getEndTime() - finishEvent.getStartTime());
                }
            }
        }
    }

    private boolean hasNoEnclosingRunningPluginApplicationForSamePlugin(BuildOperationDescriptor buildOperation, InternalPluginIdentifier plugin) {
        return !pluginApplicationTracker.hasRunningPluginApplication(buildOperation.getParentId(), pluginApplication -> pluginApplication.getPlugin().equals(plugin));
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
        Object parentId = eventConsumer.findStartedParentId(buildOperation);
        return new DefaultProjectConfigurationDescriptor(id, displayName, parentId, details.getRootDir(), details.getProjectPath());
    }

    private AbstractProjectConfigurationResult toProjectConfigurationOperationResult(OperationFinishEvent finishEvent, ProjectConfigurationResult configResult) {
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        Throwable failure = finishEvent.getFailure();
        List<InternalPluginApplicationResult> pluginApplicationResults = configResult != null ? configResult.toInternalPluginApplicationResults() : Collections.emptyList();
        if (failure != null) {
            return new DefaultProjectConfigurationFailureResult(startTime, endTime, singletonList(DefaultFailure.fromThrowable(failure)), pluginApplicationResults);
        }
        return new DefaultProjectConfigurationSuccessResult(startTime, endTime, pluginApplicationResults);
    }

    private static class ProjectConfigurationResult {

        private final Map<InternalPluginIdentifier, PluginApplicationResult> pluginApplicationResults = new ConcurrentHashMap<>();

        void increment(PluginApplication pluginApplication, long duration) {
            InternalPluginIdentifier plugin = pluginApplication.getPlugin();
            pluginApplicationResults
                .computeIfAbsent(plugin, key -> new PluginApplicationResult(plugin, pluginApplication.getApplicationId()))
                .increment(duration);
        }

        List<InternalPluginApplicationResult> toInternalPluginApplicationResults() {
            return pluginApplicationResults.values().stream()
                .sorted(comparing(PluginApplicationResult::getFirstApplicationId))
                .map(PluginApplicationResult::toInternalPluginApplicationResult)
                .collect(toCollection(ArrayList::new));
        }

    }

    private static class PluginApplicationResult {

        private AtomicLong duration = new AtomicLong();
        private final InternalPluginIdentifier plugin;
        private final long firstApplicationId;

        PluginApplicationResult(InternalPluginIdentifier plugin, long firstApplicationId) {
            this.plugin = plugin;
            this.firstApplicationId = firstApplicationId;
        }

        long getFirstApplicationId() {
            return firstApplicationId;
        }

        void increment(long duration) {
            this.duration.addAndGet(duration);
        }

        InternalPluginApplicationResult toInternalPluginApplicationResult() {
            return new DefaultPluginApplicationResult(plugin, Duration.ofMillis(duration.get()));
        }

    }

}
