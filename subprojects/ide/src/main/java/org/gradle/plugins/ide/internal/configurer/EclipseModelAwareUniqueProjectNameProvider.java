/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EclipseModelAwareUniqueProjectNameProvider implements UniqueProjectNameProvider {
    private final ProjectStateRegistry projectRegistry;
    private Map<ProjectState, String> deduplicated;
    private List<ProjectStateWrapper> reservedNames = Collections.emptyList();
    private Map<ProjectState, ProjectStateWrapper> projectToInformationMap = Collections.emptyMap();

    public EclipseModelAwareUniqueProjectNameProvider(ProjectStateRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    public synchronized void setReservedProjectNames(List<String> reservedNames) {
        this.reservedNames = reservedNames.stream().map(ProjectStateWrapper::new).collect(Collectors.toList());
        deduplicated = null;
    }

    @Override
    public String getUniqueName(Project project) {
        ProjectState state = projectRegistry.stateFor(project);
        String uniqueName = getDeduplicatedNames().get(state);
        if (uniqueName != null) {
            return uniqueName;
        }

        // ProjectStateWrapper might contain the configured eclipse project name
        ProjectStateWrapper information = projectToInformationMap.get(state);
        if (information != null) {
            return information.name;
        }
        return state.getName();
    }

    private synchronized Map<ProjectState, String> getDeduplicatedNames() {
        if (deduplicated == null) {

            projectToInformationMap = new HashMap<>();
            for (ProjectState state : projectRegistry.getAllProjects()) {
                // try to get the name from EclipseProject.name
                state.getOwner().ensureProjectsConfigured();
                EclipseModel model = state.getMutableModel().getExtensions().findByType(EclipseModel.class);
                if (model != null && model.getProject().getName() != null) {
                    projectToInformationMap.put(state, new ProjectStateWrapper(model.getProject().getName(), state, state.getParent()));
                    continue;
                }
                // fallback: take the name from the ProjectState
                projectToInformationMap.put(state, new ProjectStateWrapper(state.getName(), state, state.getParent()));
            }

            HierarchicalElementDeduplicator<ProjectStateWrapper> deduplicator = new HierarchicalElementDeduplicator<>(new ProjectPathDeduplicationAdapter(projectToInformationMap));
            List<ProjectStateWrapper> allElements = new ArrayList<>();
            allElements.addAll(reservedNames);
            allElements.addAll(projectToInformationMap.values());

            this.deduplicated = deduplicator.deduplicate(allElements).entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().project, Map.Entry::getValue));
        }
        return deduplicated;
    }

    private static class ProjectStateWrapper {
        private final String name;
        private final ProjectState project;
        private final ProjectState parent;

        public ProjectStateWrapper(String name, ProjectState project, ProjectState parent) {
            this.name = name;
            this.project = project;
            this.parent = parent;
        }

        public ProjectStateWrapper(String name) {
            this(name, null, null);
        }
    }

    private static class ProjectPathDeduplicationAdapter implements HierarchicalElementAdapter<ProjectStateWrapper> {
        private final Map<ProjectState, ProjectStateWrapper> projectToInformationMap;

        public ProjectPathDeduplicationAdapter(Map<ProjectState, ProjectStateWrapper> projectToInformationMap) {
            this.projectToInformationMap = projectToInformationMap;
        }

        @Override
        public String getName(ProjectStateWrapper element) {
            return element.name;
        }

        @Override
        public String getIdentityName(ProjectStateWrapper element) {
            return element.name;
        }

        @Override
        public ProjectStateWrapper getParent(ProjectStateWrapper element) {
            return projectToInformationMap.get(element.parent);
        }
    }

}
