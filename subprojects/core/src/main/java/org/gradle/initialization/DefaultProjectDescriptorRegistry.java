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
package org.gradle.initialization;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectIdentifier;
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

@ServiceScope(Scope.Settings.class)
public class DefaultProjectDescriptorRegistry implements ProjectDescriptorRegistry, HoldsProjectState {
    private final Map<String, DefaultProjectDescriptor> projects = new HashMap<>();
    private final Map<String, Set<DefaultProjectDescriptor>> subProjects = new HashMap<>();

    @Override
    public void addProject(DefaultProjectDescriptor project) {
        DefaultProjectDescriptor previous = projects.put(project.getPath(), project);
        if (previous != null) {
            throw new IllegalArgumentException(String.format("Multiple projects registered for path '%s'.", project.getPath()));
        }
        subProjects.put(project.getPath(), new HashSet<>());
        addProjectToParentSubProjects(project);
    }

    public DefaultProjectDescriptor removeProject(String path) {
        DefaultProjectDescriptor project = projects.remove(path);
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

    private void addProjectToParentSubProjects(DefaultProjectDescriptor project) {
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
    public Set<DefaultProjectDescriptor> getAllProjects() {
        return new HashSet<>(projects.values());
    }

    @Override
    public DefaultProjectDescriptor getRootProject() {
        return getProject(Path.ROOT.getPath());
    }

    @Override
    public @Nullable DefaultProjectDescriptor getProject(String path) {
        return projects.get(path);
    }

    @Override
    public DefaultProjectDescriptor getProject(final File projectDir) {
        Set<DefaultProjectDescriptor> projects = findAll(element -> element.getProjectDir().equals(projectDir));
        if (projects.size() > 1) {
            throw new InvalidUserDataException(String.format("Found multiple projects with project directory '%s': %s",
                projectDir, projects));
        }
        return projects.size() == 1 ? projects.iterator().next() : null;
    }

    @Override
    public Set<DefaultProjectDescriptor> getAllProjects(String path) {
        Set<DefaultProjectDescriptor> result = new HashSet<>(getSubProjects(path));
        if (projects.get(path) != null) {
            result.add(projects.get(path));
        }
        return result;
    }

    @Override
    public Set<DefaultProjectDescriptor> getSubProjects(String path) {
        return GUtil.getOrDefault(subProjects.get(path), HashSet::new);
    }

    @Override
    public Set<DefaultProjectDescriptor> findAll(Spec<? super DefaultProjectDescriptor> constraint) {
        Set<DefaultProjectDescriptor> matches = new HashSet<>();
        for (DefaultProjectDescriptor project : projects.values()) {
            if (constraint.isSatisfiedBy(project)) {
                matches.add(project);
            }
        }
        return matches;
    }

    @Override
    public void changeDescriptorPath(Path oldPath, Path newPath) {
        DefaultProjectDescriptor projectDescriptor = removeProject(oldPath.toString());
        projectDescriptor.setPath(newPath);
        addProject(projectDescriptor);
    }

}
