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
import com.google.common.collect.ImmutableSet;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class ConfigurationUsageService implements BuildService<BuildServiceParameters.None> {
    private final Map<ProjectState, Set<ConfigurationInternal>> configurations = new HashMap<>();

    public void trackConfiguration(ProjectInternal project, ConfigurationInternal configuration) {
        configurations.computeIfAbsent(project.getOwner(), state -> new HashSet<>()).add(configuration);
    }

    public String reportUsage(boolean showAllUsage) {
        if (configurations.isEmpty()) {
            return "<html><body>No configurations were created in this build.</body></html>";
        }

        AtomicBoolean wroteSomething = new AtomicBoolean(false);
        StringBuilder usageStats = new StringBuilder("<html><body>\n");

        configurations.keySet().stream()
            .sorted(Comparator.comparing(ProjectState::getName))
            .forEach(p -> {
                usageStats.append("<h1>Project: ").append(p.getDisplayName()).append("</h1>\n");
                configurations.get(p).stream()
                    .sorted(Comparator.comparing(ConfigurationInternal::getDisplayName))
                    .forEach(c -> {
                        extractUsageStats(showAllUsage, p, c, usageStats, wroteSomething);
                    });

                if (!wroteSomething.get()) {
                    usageStats.append("No configuration state was accessed incorrectly.\n");
                }
            });

        return usageStats.append("</body></html>").toString();
    }

    private void extractUsageStats(boolean showAllUsage, ProjectState p, ConfigurationInternal c, StringBuilder summarizer, AtomicBoolean wroteSomething) {
        ConfigurationRole roleAtCreation = c.getRoleAtCreation();
        summarizer.append("<h2>").append(c.getDisplayName()).append(" (").append(roleAtCreation).append(")</h2>\n");

        Map<ConfigurationRole, Map<String, Integer>> usage = c.getConfigurationStateUsage();
        usage.keySet().stream()
            .sorted(Comparator.comparing(ConfigurationRole::getName))
            .forEach(r -> {
                if (showAllUsage || isInappropriateUsage(r, roleAtCreation)) {
                    summarizer.append("<h3>Methods accessing incorrect state for Role: ").append(r.getName()).append("</h3>\n");
                    Map<String, Integer> callCounts = usage.get(r);
                    callCounts.keySet().stream()
                        .sorted()
                        .forEach(method -> summarizer.append("<p>").append(toCallStackFileLink(p, c, r, method)).append(": ").append(callCounts.get(method)).append("</p>\n"));
                    wroteSomething.set(true);
                }
            });
    }

    private String toCallStackFileLink(ProjectState p, ConfigurationInternal c, ConfigurationRole r, String methodName) {
        return "<a href=\"" + getReportPath(p, c, r, methodName) + "\">" + methodName + "</a>";
    }

    public Map<String, String> reportUsageLocations(boolean showAllUsage) {
        Map<String, StringBuilder> usageLocations = new HashMap<>();

        configurations.keySet().stream()
            .sorted(Comparator.comparing(ProjectState::getName))
            .forEach(p -> {
                configurations.get(p).stream()
                    .sorted(Comparator.comparing(ConfigurationInternal::getDisplayName))
                    .forEach(c -> {
                        extractUsageLocations(showAllUsage, p, c, usageLocations);
                    });
            });

        // Keep the max heap low by converting and removing one entry at a time
        Map<String, String> result = new HashMap<>();
        Set<String> keys = ImmutableSet.copyOf(usageLocations.keySet());
        keys.forEach(k -> {
            StringBuilder v = usageLocations.remove(k);
            result.put(k, v.toString());
        });
        return result;
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
                            String reportPath = getReportPath(p, c, r, methodName);
                            StringBuilder methodSummarizer = summarizer.computeIfAbsent(reportPath, k -> new StringBuilder("<html><body>\n"));
                            methodSummarizer.append("<h1>Call locations for ").append(methodName).append("</h1>").append("<h2>by ").append(p.getDisplayName()).append(" ").append(c.getDisplayName()).append(" in role ").append(r).append("</h2>\n");

                            AtomicInteger count = new AtomicInteger(0);
                            callLocations.get(methodName).stream()
                                .distinct()
                                .sorted(Comparator.comparing(elements -> elements[0].toString()))
                                .forEach(stackTrace -> {
                                    String strackTraceString = Arrays.stream(stackTrace)
                                        .limit(25) // Limit to first 25 elements for brevity
                                        .map(StackTraceElement::toString)
                                        .map(e -> "\t" + e)
                                        .collect(Collectors.joining("\n"));
                                    methodSummarizer.append("<h3>Call ").append(count.incrementAndGet()).append("</h3><p>").append(strackTraceString).append("</p>\n");

                                });
                        });
                }
            });

        summarizer.values().forEach(s -> s.append("</body></html>"));
    }

    private String getReportPath(ProjectState p, ConfigurationInternal c, ConfigurationRole r, String methodName) {
        return p.getName() + "/" + c.getName() + "/" + r.getName() + "/" + methodName + ".html";
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
