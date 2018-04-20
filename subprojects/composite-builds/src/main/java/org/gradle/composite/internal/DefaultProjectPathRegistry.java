/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DefaultProjectPathRegistry implements ProjectStateRegistry {
    // TODO: Synchronization
    private final Map<Path, ProjectPathEntry> allProjects = Maps.newLinkedHashMap();

    void add(Path projectIdentityPath, String projectName, ProjectComponentIdentifier identifier, boolean isImplicitBuild) {
        allProjects.put(projectIdentityPath, new ProjectPathEntry(projectIdentityPath, projectName, identifier, isImplicitBuild));
    }

    @Override
    public Collection<? extends ProjectState> getAllProjects() {
        return allProjects.values();
    }

    @Override
    public Collection<? extends ProjectState> getAllExplicitProjects() {
        return filterProjectPaths(false);
    }

    @Override
    public Collection<? extends ProjectState> getAllImplicitProjects() {
        return filterProjectPaths(true);
    }

    private Collection<? extends ProjectState> filterProjectPaths(final boolean isAddedImplicitly) {
        List<ProjectState> result = new ArrayList<ProjectState>();
        for (ProjectPathEntry entry : allProjects.values()) {
            if (entry.isAddedImplicitly == isAddedImplicitly) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public ProjectState forProject(Project project) {
        return allProjects.get(((ProjectInternal) project).getIdentityPath());
    }

    private class ProjectPathEntry implements ProjectState {
        private final String projectName;
        private final ProjectComponentIdentifier identifier;
        private final boolean isAddedImplicitly;
        private final Path projectIdentityPath;

        ProjectPathEntry(Path projectIdentityPath, String projectName, ProjectComponentIdentifier identifier, boolean isAddedImplicitly) {
            this.projectIdentityPath = projectIdentityPath;
            this.projectName = projectName;
            this.identifier = identifier;
            this.isAddedImplicitly = isAddedImplicitly;
        }

        @Override
        public String toString() {
            return identifier.getDisplayName();
        }

        @Nullable
        @Override
        public ProjectState getParent() {
            return projectIdentityPath.getParent() == null ? null : allProjects.get(projectIdentityPath.getParent());
        }

        @Override
        public String getName() {
            return projectName;
        }

        @Override
        public ProjectComponentIdentifier getComponentIdentifier() {
            return identifier;
        }
    }
}
