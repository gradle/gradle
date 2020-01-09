/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Project;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultProjectModuleFactory implements ProjectModuleFactory {
    private static final Splitter SPLITTER = Splitter.on(':')
        .omitEmptyStrings();

    private final Map<Project, Module> projectToModule = Maps.newConcurrentMap();

    public DefaultProjectModuleFactory() {
    }

    private List<Project> findDuplicates(Project project) {
        Set<Project> projects = project.getRootProject().getAllprojects();
        String current = toGroupAndArtifact(project);
        List<Project> duplicates = null;
        for (Project projectIdentifier : projects) {
            if (project != projectIdentifier) {
                String ga = toGroupAndArtifact(projectIdentifier);
                if (current.equals(ga)) {
                    if (duplicates == null) {
                        duplicates = Lists.newArrayList();
                    }
                    duplicates.add(projectIdentifier);
                }
            }
        }
        return duplicates == null ? Collections.emptyList() : duplicates;
    }

    private static String toGroupAndArtifact(Project projectIdentifier) {
        return projectIdentifier.getGroup() + ":" + projectIdentifier.getName();
    }

    @Override
    public Module getModule(Project project) {
        return projectToModule.computeIfAbsent(project, this::createId);
    }

    private Module createId(Project project) {
        return new DynamicDeduplicatingModuleProjectIdentifier(project);
    }

    private abstract class AbstractProjectBackedModule implements ProjectBackedModule {

        private final Project project;

        @Override
        public Project getProject() {
            return project;
        }

        public AbstractProjectBackedModule(Project project) {
            this.project = project;
        }

        @Override
        public List<Project> getProjectsWithSameCoordinates() {
            List<Project> ids = findDuplicates(project);
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            return ids.stream()
                .filter(id -> id != project)
                .collect(Collectors.toList());
        }

        @Override
        public String getGroup() {
            return String.valueOf(project.getGroup());
        }

        @Override
        public String getVersion() {
            return project.getVersion().toString();
        }

        @Override
        public String getStatus() {
            return project.getStatus().toString();
        }

        @Override
        public String getProjectPath() {
            return project.getPath();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AbstractProjectBackedModule that = (AbstractProjectBackedModule) o;

            if (!project.equals(that.project)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return project.hashCode();
        }
    }

    private class DynamicDeduplicatingModuleProjectIdentifier extends AbstractProjectBackedModule {
        private final Project project;

        private DynamicDeduplicatingModuleProjectIdentifier(Project project) {
            super(project);
            this.project = project;
        }

        @Override
        public String getName() {
            List<Project> duplicates = findDuplicates(project);
            if (duplicates.isEmpty()) {
                return project.getName();
            }
            List<String> strings = SPLITTER.splitToList(project.getPath());
            if (strings.size() <= 1) {
                return project.getName();
            }
            return String.join("-", strings.subList(0, strings.size() - 1)) + "-" + project.getName();
        }
    }
}
