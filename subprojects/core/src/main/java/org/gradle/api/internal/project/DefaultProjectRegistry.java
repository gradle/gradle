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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ServiceScope(Scope.Build.class)
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

    public ProjectInternal removeProject(String path) {
        ProjectInternal project = projects.remove(path);
        assert project != null;
        subProjects.remove(path);
        ProjectIdentifier loopProject = project.getParentIdentifier();
        while (loopProject != null) {
            subProjects.get(loopProject.getPath()).remove(project);
            loopProject = loopProject.getParentIdentifier();
        }
        return project;
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
    public int size() {
        return projects.size();
    }

    @Override
    public Set<ProjectInternal> getAllProjects() {
        return new HashSet<>(projects.values());
    }

    @Override
    public ProjectInternal getRootProject() {
        return getProject(Path.ROOT.getPath());
    }

    @Override
    public @Nullable ProjectInternal getProject(String path) {
        return projects.get(path);
    }

    @Override
    public ProjectInternal getProject(final File projectDir) {
        Set<ProjectInternal> projects = findAll(new Spec<ProjectInternal>() {
            @Override
            public boolean isSatisfiedBy(ProjectInternal element) {
                return element.getProjectDir().equals(projectDir);
            }
        });
        if (projects.size() > 1) {
            throw new InvalidUserDataException(String.format("Found multiple projects with project directory '%s': %s",
                projectDir, projects));
        }
        return projects.size() == 1 ? projects.iterator().next() : null;
    }

    @Override
    public Set<ProjectInternal> getAllProjects(String path) {
        Set<ProjectInternal> result = new HashSet<>(getSubProjects(path));
        if (projects.get(path) != null) {
            result.add(projects.get(path));
        }
        return result;
    }

    @Override
    public Set<ProjectInternal> getSubProjects(String path) {
        return GUtil.getOrDefault(subProjects.get(path), HashSet::new);
    }

    @Override
    public Set<ProjectInternal> findAll(Spec<? super ProjectInternal> constraint) {
        Set<ProjectInternal> matches = new HashSet<>();
        for (ProjectInternal project : projects.values()) {
            if (constraint.isSatisfiedBy(project)) {
                matches.add(project);
            }
        }
        return matches;
    }
}
