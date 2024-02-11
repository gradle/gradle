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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides access to essential details on projects and their configurations.
 *
 * Instances are expected to be serialization-friendly.
 *
 * @param <P> the kind of project details being projected
 * @param <C> the kind of configuration details being projected
 */
public interface ProjectsWithConfigurations<P extends ProjectDetails, C extends ConfigurationDetails> {

    Set<P> getProjects();

    Iterable<C> getConfigurationsFor(P project);

    static <P extends ProjectDetails, C extends ConfigurationDetails> ProjectsWithConfigurations<P, C> from(Iterable<Project> projects, Function<Project, P> projectProjector,  Function<Project, Stream<? extends C>> configurationProjector) {
        Map<P, Iterable<C>> details = new LinkedHashMap<>();
        projects.forEach(p -> {
            P projectDetails = projectProjector.apply(p);
            Iterable<C> configurationDetails = configurationProjector.apply(p).collect(Collectors.toList());
            details.put(projectDetails, configurationDetails);
        });
        return new ProjectsWithConfigurations<P, C>() {
            @Override
            public Set<P> getProjects() {
                return details.keySet();
            }

            @Override
            public Iterable<C> getConfigurationsFor(P project) {
                return details.getOrDefault(project, Collections.emptySet());
            }
        };
    }
}
