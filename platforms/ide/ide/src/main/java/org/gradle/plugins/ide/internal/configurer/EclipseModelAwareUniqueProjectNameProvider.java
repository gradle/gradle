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

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class EclipseModelAwareUniqueProjectNameProvider extends AbstractUniqueProjectNameProvider {

    @Nullable
    private Map<ProjectIdentity, String> deduplicated;
    private List<ProjectStateWrapper> reservedNames = Collections.emptyList();
    private Map<ProjectIdentity, ProjectStateWrapper> projectToInformationMap = Collections.emptyMap();

    public EclipseModelAwareUniqueProjectNameProvider(ProjectStateRegistry projectRegistry) {
        super(projectRegistry);
    }

    public synchronized void setReservedProjectNames(List<String> reservedNames) {
        this.reservedNames = reservedNames.stream().map(ProjectStateWrapper::new).collect(Collectors.toList());
        deduplicated = null;
    }

    @Override
    public String getUniqueName(ProjectIdentity projectIdentity) {
        String uniqueName = getDeduplicatedNames().get(projectIdentity);
        if (uniqueName != null) {
            return uniqueName;
        }

        // ProjectStateWrapper might contain the configured eclipse project name
        ProjectStateWrapper information = projectToInformationMap.get(projectIdentity);
        if (information != null) {
            return information.name;
        }
        return projectIdentity.getProjectName();
    }

    private synchronized Map<ProjectIdentity, String> getDeduplicatedNames() {
        if (deduplicated == null) {

            projectToInformationMap = new HashMap<>();
            for (ProjectState state : projectRegistry.getAllProjects()) {
                String projectNameForEclipse = getName(state);
                projectToInformationMap.put(state.getIdentity(), new ProjectStateWrapper(projectNameForEclipse, state));
            }

            HierarchicalElementDeduplicator<ProjectStateWrapper> deduplicator = new HierarchicalElementDeduplicator<>(new ProjectPathDeduplicationAdapter(projectToInformationMap));
            List<ProjectStateWrapper> allElements = new ArrayList<>();
            allElements.addAll(reservedNames);
            allElements.addAll(projectToInformationMap.values());

            this.deduplicated = deduplicator.deduplicate(allElements).entrySet().stream()
                .collect(toMap(e -> e.getKey().project.getIdentity(), Map.Entry::getValue));
        }
        return deduplicated;
    }

    private static String getName(ProjectState state) {
        // try to get the name from EclipseProject.name
        state.getOwner().ensureProjectsConfigured();

        EclipseModel model = state.getMutableModel().getExtensions().findByType(EclipseModel.class);
        if (model != null && model.getProject().getName() != null) {
            return model.getProject().getName();
        }

        // fallback: take the name from the ProjectState
        return state.getName();
    }

    private static class ProjectStateWrapper {
        private final String name;
        @Nullable
        private final ProjectState project;

        public ProjectStateWrapper(String name, @Nullable ProjectState project) {
            this.name = name;
            this.project = project;
        }

        public ProjectStateWrapper(String name) {
            this(name, null);
        }
    }

    private class ProjectPathDeduplicationAdapter implements HierarchicalElementAdapter<ProjectStateWrapper> {
        private final Map<ProjectIdentity, ProjectStateWrapper> projectToInformationMap;

        public ProjectPathDeduplicationAdapter(Map<ProjectIdentity, ProjectStateWrapper> projectToInformationMap) {
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
        @Nullable
        public ProjectStateWrapper getParent(ProjectStateWrapper element) {
            if (element.project == null) {
                return null;
            }

            ProjectIdentity parentInBuildTree = findParentInBuildTree(element.project.getIdentity());
            return parentInBuildTree == null ? null : projectToInformationMap.get(parentInBuildTree);
        }
    }

}
