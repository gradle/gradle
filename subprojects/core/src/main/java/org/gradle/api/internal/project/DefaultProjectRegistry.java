/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// This default implementation is referenced by the Develocity plugin in ImportJUnitXmlReports.
// We must leave this type in until we no longer support a Develocity plugin version that use this API.
@Deprecated
@ServiceScope(Scope.Build.class)
@UsedByScanPlugin("ImportJUnitXmlReports")
public class DefaultProjectRegistry implements ProjectRegistry, HoldsProjectState {

    private final Map<String, ProjectInternal> projects = new HashMap<>();
    private final Map<String, Set<ProjectInternal>> subProjects = new HashMap<>();

    @Override
    public void addProject(ProjectInternal project) {
        ProjectInternal previous = projects.put(project.getPath(), project);
        if (previous != null) {
            throw new IllegalArgumentException(String.format("Multiple projects registered for path '%s'.", project.getPath()));
        }
        subProjects.put(project.getPath(), new HashSet<>());
        addProjectToParentSubProjects(project);
    }

    @Override
    public void discardAll() {
        projects.clear();
        subProjects.clear();
    }

    private void addProjectToParentSubProjects(ProjectInternal project) {
        ProjectIdentifier loopProject = project.getParentIdentifier();
        while (loopProject != null) {
            subProjects.get(loopProject.getPath()).add(project);
            loopProject = loopProject.getParentIdentifier();
        }
    }

    @Override
    public @Nullable ProjectIdentifier getProject(String path) {
        return projects.get(path);
    }

    @Override
    public @Nullable ProjectInternal getProjectInternal(String path) {
        return projects.get(path);
    }

    @Override
    public Set<ProjectInternal> getAllProjects(String path) {
        Set<ProjectInternal> result = new HashSet<>(getSubProjects(path));
        if (projects.get(path) != null) {
            result.add(projects.get(path));
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<ProjectInternal> getSubProjects(String path) {
        Set<ProjectInternal> subprojects = subProjects.get(path);
        return subprojects != null && !subprojects.isEmpty() ? Collections.unmodifiableSet(subprojects) : Collections.emptySet();
    }

}
