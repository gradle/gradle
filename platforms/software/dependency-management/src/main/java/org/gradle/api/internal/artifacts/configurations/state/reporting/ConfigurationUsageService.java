/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.state.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public abstract class ConfigurationUsageService implements BuildService<BuildServiceParameters.None> {
    private final Map<ProjectState, Set<ConfigurationInternal>> configurations = new HashMap<>();

    public void trackConfiguration(ProjectInternal project, ConfigurationInternal configuration) {
        configurations.computeIfAbsent(project.getOwner(), state -> new HashSet<>()).add(configuration);
    }

    public String reportUsage(boolean showAllUsage) {
        if (configurations.isEmpty()) {
            return "No configurations were created in this build.";
        }

        AtomicBoolean wroteSomething = new AtomicBoolean(false);
        StringBuilder usageStats = new StringBuilder();

        configurations.keySet().stream()
            .sorted()
            .forEach(p -> {
                usageStats.append("Project: ").append(p.getDisplayName()).append("\n");
                configurations.get(p).stream()
                    .sorted(Comparator.comparing(ConfigurationInternal::getDisplayName))
                    .forEach(c -> {
                        extractUsageStats(showAllUsage, c, usageStats, wroteSomething);
                    });

                if (!wroteSomething.get()) {
                    usageStats.append("No configuration state was accessed incorrectly.\n");
                }
            });

        return usageStats.toString();
    }

    private void extractUsageStats(boolean showAllUsage, ConfigurationInternal c, StringBuilder summarizer, AtomicBoolean wroteSomething) {
        ConfigurationRole roleAtCreation = c.getRoleAtCreation();
        summarizer.append("  ").append(c.getDisplayName()).append(" (").append(roleAtCreation).append(")\n");

        Map<ConfigurationRole, Map<String, Integer>> usage = c.getConfigurationStateUsage();
        usage.keySet().stream()
            .sorted(Comparator.comparing(ConfigurationRole::getName))
            .forEach(r -> {
                if (showAllUsage || isInappropriateUsage(r, roleAtCreation)) {
                    summarizer.append("    Role: ").append(r.getName()).append("\n");
                    Map<String, Integer> callCounts = usage.get(r);
                    callCounts.keySet().stream()
                        .sorted()
                        .forEach(method -> summarizer.append("      ").append(method).append(": ").append(callCounts.get(method)).append("\n"));
                    wroteSomething.set(true);
                }
            });
    }

    public Map<String, String> reportUsageLocations(boolean showAllUsage) {
        Map<String, StringBuilder> usageLocations = new HashMap<>();

        configurations.keySet().stream()
            .sorted()
            .forEach(p -> {
                configurations.get(p).stream()
                    .sorted(Comparator.comparing(ConfigurationInternal::getDisplayName))
                    .forEach(c -> {
                        extractUsageLocations(showAllUsage, p, c, usageLocations);
                    });
            });

        return usageLocations.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }

    private void extractUsageLocations(boolean showAllUsage, ProjectState p, ConfigurationInternal c, Map<String, StringBuilder> summarizer) {
        ConfigurationRole roleAtCreation = c.getRoleAtCreation();

        ImmutableMap<ConfigurationRole, Multimap<String, StackTraceElement[]>> usageLocations = c.getConfigurationStateUsageLocations();
        usageLocations.keySet().stream()
            .sorted(Comparator.comparing(ConfigurationRole::getName))
            .forEach(r -> {
                if (showAllUsage || isInappropriateUsage(r, roleAtCreation)) {
                    Multimap<String, StackTraceElement[]> callLocations = usageLocations.get(r);
                    callLocations.keySet().stream()
                        .sorted()
                        .forEach(methodName -> {
                            String reportPath = p.getName() + "/" + c.getName() + "/" + r.getName() + "/" + methodName + ".html";
                            StringBuilder methodSummarizer = summarizer.computeIfAbsent(reportPath, k -> new StringBuilder());
                            methodSummarizer.append("Call locations for ").append(methodName).append(" by ").append(p.getDisplayName()).append(" ").append(c.getDisplayName()).append(" in role ").append(r).append(":\n");
                            callLocations.get(methodName).stream()
                                .distinct()
                                .sorted(Comparator.comparing(elements -> elements[0].toString()))
                                .forEach(stackTrace -> {
                                    String strackTraceString = Arrays.stream(stackTrace)
                                        .limit(25) // Limit to first 25 elements for brevity
                                        .map(StackTraceElement::toString)
                                        .collect(Collectors.joining("\n", "\t", ""));
                                    methodSummarizer.append(strackTraceString).append("\n");
                                });
                        });
                }
            });
    }

    /**
     * Checks if the usage of the given role is appropriate based on the role at creation.
     * <p>
     * This is used to filter out usages that are not relevant for the role at creation.
     * <p>
     * This method uses a {@code contains} check so that `Resolvable Dependency Scope` encompasses both `Resolvable` and `Dependency Scope`
     * as appropriate usages.
     *
     * @param r the role being checked
     * @param roleAtCreation the role at the time of configuration's creation
     * @return {@code true} if the usage is inappropriate; {@code false} otherwise
     */
    private static boolean isInappropriateUsage(ConfigurationRole r, ConfigurationRole roleAtCreation) {
        return !roleAtCreation.getName().contains(r.getName());
    }
}
