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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;

import java.util.Map;

public class DefaultUniqueProjectNameProvider implements UniqueProjectNameProvider {
    private final ProjectStateRegistry projectRegistry;
    private Map<ProjectState, String> deduplicated;

    public DefaultUniqueProjectNameProvider(ProjectStateRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public String getUniqueName(Project project) {
        String uniqueName = getDeduplicatedNames().get(projectRegistry.stateFor(project));
        if (uniqueName != null) {
            return uniqueName;
        }
        return project.getName();
    }

    private synchronized Map<ProjectState, String> getDeduplicatedNames() {
        if (deduplicated == null) {
            HierarchicalElementDeduplicator<ProjectState> deduplicator = new HierarchicalElementDeduplicator<ProjectState>(new ProjectPathDeduplicationAdapter());
            this.deduplicated = deduplicator.deduplicate(projectRegistry.getAllProjects());
        }
        return deduplicated;
    }

    private static class ProjectPathDeduplicationAdapter implements HierarchicalElementAdapter<ProjectState> {
        @Override
        public String getName(ProjectState element) {
            return element.getName();
        }

        @Override
        public ProjectState getParent(ProjectState element) {
            return element.getParent();
        }
    }
}
