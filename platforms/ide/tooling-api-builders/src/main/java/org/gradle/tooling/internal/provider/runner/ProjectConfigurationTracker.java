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
import org.gradle.internal.build.event.types.DefaultPluginApplicationResult;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalProjectConfigurationResult.InternalPluginApplicationResult;
import org.gradle.tooling.internal.provider.runner.PluginApplicationTracker.PluginApplication;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toCollection;

class ProjectConfigurationTracker implements BuildOperationTracker {

    private final Map<OperationIdentifier, ProjectConfigurationResult> results = new ConcurrentHashMap<>();
    private final BuildOperationAncestryTracker ancestryTracker;
    private final PluginApplicationTracker pluginApplicationTracker;

    ProjectConfigurationTracker(BuildOperationAncestryTracker ancestryTracker, PluginApplicationTracker pluginApplicationTracker) {
        this.ancestryTracker = ancestryTracker;
        this.pluginApplicationTracker = pluginApplicationTracker;
    }

    @Override
    public List<? extends BuildOperationTracker> getTrackers() {
        return ImmutableList.of(pluginApplicationTracker);
    }

    public List<InternalPluginApplicationResult> resultsFor(OperationIdentifier buildOperation) {
        ProjectConfigurationResult result = results.remove(buildOperation);
        if (result == null) {
            throw new IllegalStateException("Project configuration results are not available for build operation " + buildOperation);
        }
        return result.toInternalPluginApplicationResults();
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (buildOperation.getDetails() instanceof ConfigureProjectBuildOperationType.Details) {
            results.put(buildOperation.getId(), new ProjectConfigurationResult());
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        PluginApplication pluginApplication = pluginApplicationTracker.getRunningPluginApplication(buildOperation.getId());
        if (pluginApplication != null) {
            ancestryTracker.findClosestExistingAncestor(buildOperation.getParentId(), results::get).ifPresent(result -> {
                if (hasNoEnclosingRunningPluginApplicationForSamePlugin(buildOperation, pluginApplication.getPlugin())) {
                    result.increment(pluginApplication, finishEvent.getEndTime() - finishEvent.getStartTime());
                }
            });
        }
    }

    private boolean hasNoEnclosingRunningPluginApplicationForSamePlugin(BuildOperationDescriptor buildOperation, InternalPluginIdentifier plugin) {
        return !pluginApplicationTracker.hasRunningPluginApplication(buildOperation.getParentId(), pluginApplication -> pluginApplication.getPlugin().equals(plugin));
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
        private final AtomicLong duration = new AtomicLong();
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
