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
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultProjectRegistry<T extends ProjectIdentifier> implements ProjectRegistry<T>, HoldsProjectState {
    private final Map<String, T> projects = new HashMap<String, T>();
    private final Map<String, Set<T>> subProjects = new HashMap<String, Set<T>>();

    @Override
    public void addProject(T project) {
        T previous = projects.put(project.getPath(), project);
        if (previous != null) {
            throw new IllegalArgumentException(String.format("Multiple projects registered for path '%s'.", project.getPath()));
        }
        subProjects.put(project.getPath(), new HashSet<T>());
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
    public Set<T> getAllProjects() {
        return new HashSet<T>(projects.values());
    }

    @Override
    public T getRootProject() {
        return getProject(Path.ROOT.getPath());
    }

    @Override
    public T getProject(String path) {
        return projects.get(path);
    }

    @Override
    public T getProject(final File projectDir) {
        Set<T> projects = findAll(new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T element) {
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
    public Set<T> getAllProjects(String path) {
        Set<T> result = new HashSet<T>(getSubProjects(path));
        if (projects.get(path) != null) {
            result.add(projects.get(path));
        }
        return result;
    }

    @Override
    public Set<T> getSubProjects(String path) {
        return GUtil.getOrDefault(subProjects.get(path), HashSet::new);
    }

    @Override
    public Set<T> findAll(Spec<? super T> constraint) {
        Set<T> matches = new HashSet<T>();
        for (T project : projects.values()) {
            if (constraint.isSatisfiedBy(project)) {
                matches.add(project);
            }
        }
        return matches;
    }
}
