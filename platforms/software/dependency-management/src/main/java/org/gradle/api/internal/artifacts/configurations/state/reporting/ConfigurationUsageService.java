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

import org.gradle.api.Project;
import org.gradle.api.ProjectState;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ConfigurationUsageService implements BuildService<BuildServiceParameters.None> {
    private final Map<ProjectState, Set<ConfigurationInternal>> configurations = new HashMap<>();

    public void trackConfigurationUsage(Project project, ConfigurationInternal configuration) {
        configurations.computeIfAbsent(project.getState(), state -> new HashSet<>()).add(configuration);
    }

    public String reportUsage() {
        if (configurations.isEmpty()) {
            return "No configurations were created in this build.";
        }

        StringBuilder report = new StringBuilder("Configuration Usage Report:\n");
        for (Map.Entry<ProjectState, Set<ConfigurationInternal>> entry : configurations.entrySet()) {
            ProjectState state = entry.getKey();
            Set<ConfigurationInternal> configs = entry.getValue();
            report.append("Project State: ").append(state).append("\n");
            for (ConfigurationInternal config : configs) {
                report.append(" - Configuration: ").append(config.getName()).append("\n");
            }
        }
        return report.toString();
    }
}
