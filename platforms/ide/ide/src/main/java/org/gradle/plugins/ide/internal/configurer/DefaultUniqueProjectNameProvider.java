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
package org.gradle.plugins.ide.internal.configurer;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class DefaultUniqueProjectNameProvider implements UniqueProjectNameProvider {
    private final ProjectStateRegistry projectRegistry;
    private Map<ProjectIdentity, String> deduplicated;

    public DefaultUniqueProjectNameProvider(ProjectStateRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public String getUniqueName(Project project) {
        ProjectState projectState = projectRegistry.stateFor(project);
        String uniqueName = getDeduplicatedNames().get(projectState.getIdentity());
        if (uniqueName != null) {
            return uniqueName;
        }
        return project.getName();
    }

    private synchronized Map<ProjectIdentity, String> getDeduplicatedNames() {
        if (deduplicated == null) {
            HierarchicalElementDeduplicator<ProjectIdentity> deduplicator = new HierarchicalElementDeduplicator<>(new ProjectPathDeduplicationAdapter());
            List<ProjectIdentity> allProjects = projectRegistry.getAllProjects().stream()
                .map(ProjectState::getIdentity)
                .collect(toList());
            this.deduplicated = deduplicator.deduplicate(allProjects);
        }
        return deduplicated;
    }

    private class ProjectPathDeduplicationAdapter implements HierarchicalElementAdapter<ProjectIdentity> {
        @Override
        public String getName(ProjectIdentity element) {
            return element.getProjectName();
        }

        @Override
        public String getIdentityName(ProjectIdentity element) {
            String identityName = element.getBuildTreePath().getName();
            return identityName != null ? identityName : element.getProjectName();
        }

        @Override
        @Nullable
        public ProjectIdentity getParent(ProjectIdentity element) {
            // Note that this "parent" may belong to a different build in the build tree
            Path parentInBuildTreePath = element.getBuildTreePath().getParent();
            if (parentInBuildTreePath == null) {
                return null;
            }
            ProjectState parentInBuildTreeState = projectRegistry.findProjectState(parentInBuildTreePath);
            if (parentInBuildTreeState == null) {
                return null;
            }
            return parentInBuildTreeState.getIdentity();
        }
    }
}
