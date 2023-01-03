/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ProjectsWithConfigurations {
    Set<ProjectDetails> getProjects();
    Iterable<ConfigurationDetails> getConfigurationsFor(ProjectDetails project);
    static ProjectsWithConfigurations from(Iterable<Project> projects, Function<Project, Stream<? extends Configuration>> configurations) {
        Map<ProjectDetails, Iterable<ConfigurationDetails>> details = new LinkedHashMap<>();
        projects.forEach(p -> {
            ProjectDetails projectDetails = ProjectDetails.of(p);
            Iterable<ConfigurationDetails> configurationDetails = configurations.apply(p).map(ConfigurationDetails::of).collect(Collectors.toList());
            details.put(projectDetails, configurationDetails);
        });
        return new ProjectsWithConfigurations() {
            @Override
            public Set<ProjectDetails> getProjects() {
                return details.keySet();
            }

            @Override
            public Iterable<ConfigurationDetails> getConfigurationsFor(ProjectDetails project) {
                return details.getOrDefault(project, Collections.emptySet());
            }
        };
    }
}
