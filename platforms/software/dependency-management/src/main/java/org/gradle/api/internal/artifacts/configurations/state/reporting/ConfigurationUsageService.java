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

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRole;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ConfigurationUsageService implements BuildService<BuildServiceParameters.None> {
    private final Map<ProjectState, Set<ConfigurationInternal>> configurations = new HashMap<>();

    public void trackConfigurationUsage(ProjectInternal project, ConfigurationInternal configuration) {
        configurations.computeIfAbsent(project.getOwner(), state -> new HashSet<>()).add(configuration);
    }

    public String reportUsage() {
        if (configurations.isEmpty()) {
            return "No configurations were created in this build.";
        }

        StringBuilder result = new StringBuilder();
        configurations.keySet().stream()
            .sorted()
            .forEach(p -> {
                result.append("Project: ").append(p.getDisplayName()).append("\n");
                configurations.get(p).stream()
                    .sorted(Comparator.comparing(ConfigurationInternal::getDisplayName))
                    .forEach(c -> {
                        result.append(c.getDisplayName()).append(" (").append(c.getRoleAtCreation()).append(")\n");
                        Map<ConfigurationRole, Map<String, Integer>> usage = c.getConfigurationStateUsage();
                        usage.keySet().stream()
                            .sorted(Comparator.comparing(ConfigurationRole::getName))
                            .forEach(r -> {
                                result.append("    Role: ").append(r.getName()).append("\n");
                                Map<String, Integer> details = usage.get(r);
                                details.keySet().stream()
                                    .sorted()
                                    .forEach(method -> {
                                        result.append("      ").append(method).append(": ").append(details.get(method)).append("\n");
                                    });
                            });
                    });
            });

        return result.toString();
    }
}
