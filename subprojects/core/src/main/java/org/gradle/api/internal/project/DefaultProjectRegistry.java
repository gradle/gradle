/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

public class DefaultProjectRegistry<T extends ProjectIdentifier> implements ProjectRegistry<T>, HoldsProjectState {
    private final Map<String, T> projects = new HashMap<>();
    private final Map<String, Set<T>> subProjects = new HashMap<>();

    @Override
    public void addProject(T project) {
        T previous = projects.put(project.getPath(), project);
        if (previous != null) {
            throw new IllegalArgumentException(String.format("Multiple projects registered for path '%s'.", project.getPath()));
        }
        subProjects.put(project.getPath(), new HashSet<>());
        addProjectToParentSubProjects(project);
    }

    public T removeProject(String path) {
        T project = projects.remove(path);
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

    private void addProjectToParentSubProjects(T project) {
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
    public Collection<T> getAllProjects() {
        return projects.values();
    }

    @Override
    public T getRootProject() {
        return getProject(Path.ROOT.getPath());
    }

    @Nullable
    @Override
    public T getProject(String path) {
        return projects.get(path);
    }

    @Nullable
    @Override
    public T getProject(final File projectDir) {
        Set<T> projects = findAll(project -> project.getProjectDir().equals(projectDir));
        if (projects.size() > 1) {
            throw new InvalidUserDataException(String.format("Found multiple projects with project directory '%s': %s",
                projectDir, projects));
        }
        return projects.size() == 1
            ? projects.iterator().next()
            : null;
    }

    @Override
    public Set<T> getAllProjects(String path) {
        T project = projects.get(path);
        if (project == null) {
            return emptySet();
        }
        Set<T> subProjects = getSubProjects(path);
        Set<T> result = new HashSet<>(subProjects.size() + 1);
        result.addAll(subProjects);
        result.add(project);
        return result;
    }

    @Override
    public Set<T> getSubProjects(String path) {
        return subProjects.getOrDefault(path, emptySet());
    }

    @Override
    public Set<T> findAll(Spec<? super T> constraint) {
        Set<T> matches = new HashSet<>();
        for (T project : projects.values()) {
            if (constraint.isSatisfiedBy(project)) {
                matches.add(project);
            }
        }
        return matches;
    }
}
