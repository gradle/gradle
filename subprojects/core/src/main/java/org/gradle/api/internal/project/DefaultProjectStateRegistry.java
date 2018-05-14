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
package org.gradle.api.internal.project;

import com.google.common.collect.Maps;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildState;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

public class DefaultProjectStateRegistry implements ProjectStateRegistry {
    private final Object lock = new Object();
    private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();
    private final Map<ProjectComponentIdentifier, ProjectStateImpl> projectsById = Maps.newLinkedHashMap();
    private final Map<Pair<BuildIdentifier, Path>, ProjectStateImpl> projectsByCompId = Maps.newLinkedHashMap();

    public void registerProjects(BuildState owner) {
        synchronized (lock) {
            for (DefaultProjectDescriptor descriptor : owner.getLoadedSettings().getProjectRegistry().getAllProjects()) {
                Path identityPath = owner.getIdentityPathForProject(descriptor.path());
                ProjectComponentIdentifier projectIdentifier = owner.getIdentifierForProject(descriptor.path());
                ProjectStateImpl projectState = new ProjectStateImpl(owner, identityPath, descriptor.getName(), projectIdentifier);
                projectsByPath.put(identityPath, projectState);
                projectsById.put(projectIdentifier, projectState);
                projectsByCompId.put(Pair.of(owner.getBuildIdentifier(), descriptor.path()), projectState);
            }
        }
    }

    @Override
    public void register(BuildState owner, ProjectInternal project) {
        synchronized (lock) {
            Path identityPath = project.getIdentityPath();
            ProjectComponentIdentifier projectIdentifier = owner.getIdentifierForProject(project.getProjectPath());
            ProjectStateImpl projectState = new ProjectStateImpl(owner, identityPath, project.getName(), projectIdentifier);
            projectsByPath.put(projectState.projectIdentityPath, projectState);
            projectsById.put(projectState.identifier, projectState);
            projectsByCompId.put(Pair.of(owner.getBuildIdentifier(), project.getProjectPath()), projectState);
        }
    }

    @Override
    public Collection<ProjectStateImpl> getAllProjects() {
        synchronized (lock) {
            return projectsByPath.values();
        }
    }

    @Override
    public ProjectState stateFor(Project project) {
        synchronized (lock) {
            ProjectStateImpl projectState = projectsByPath.get(((ProjectInternal) project).getIdentityPath());
            if (projectState == null) {
                throw new IllegalArgumentException("Could not find state for " + project);
            }
            return projectState;
        }
    }

    @Override
    public ProjectState stateFor(ProjectComponentIdentifier identifier) {
        synchronized (lock) {
            ProjectStateImpl projectState = projectsById.get(identifier);
            if (projectState == null) {
                throw new IllegalArgumentException(identifier.getDisplayName() + " not found.");
            }
            return projectState;
        }
    }

    @Override
    public ProjectState stateFor(BuildIdentifier buildIdentifier, Path projectPath) {
        synchronized (lock) {
            ProjectStateImpl projectState = projectsByCompId.get(Pair.of(buildIdentifier, projectPath));
            if (projectState == null) {
                throw new IllegalArgumentException(buildIdentifier + " project " + projectPath + " not found.");
            }
            return projectState;
        }
    }

    private class ProjectStateImpl implements ProjectState {
        private final String projectName;
        private final ProjectComponentIdentifier identifier;
        private final BuildState owner;
        private final Path projectIdentityPath;

        ProjectStateImpl(BuildState owner, Path projectIdentityPath, String projectName, ProjectComponentIdentifier identifier) {
            this.owner = owner;
            this.projectIdentityPath = projectIdentityPath;
            this.projectName = projectName;
            this.identifier = identifier;
        }

        @Override
        public String toString() {
            return identifier.getDisplayName();
        }

        @Override
        public BuildState getOwner() {
            return owner;
        }

        @Nullable
        @Override
        public ProjectState getParent() {
            return projectIdentityPath.getParent() == null ? null : projectsByPath.get(projectIdentityPath.getParent());
        }

        @Override
        public String getName() {
            return projectName;
        }

        @Override
        public ProjectComponentIdentifier getComponentIdentifier() {
            return identifier;
        }

        @Override
        public <T> void withMutableState(Runnable action) {
            withMutableState(Factories.toFactory(action));
        }

        @Override
        public <T> T withMutableState(Factory<? extends T> action) {
            synchronized (this) {
                return action.create();
            }
        }
    }
}
