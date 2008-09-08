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

import org.gradle.api.Project;
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.GUtil;

import java.util.*;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultProjectRegistry implements IProjectRegistry {
    private Map<String, Project> projects;

    private Map<String, Set<Project>> subProjects;

    private Map<File, Project> projectDir2Project;

    public DefaultProjectRegistry() {
        init();
    }

    private void init() {
        projects = new HashMap<String, Project>();
        subProjects = new HashMap<String, Set<Project>>();
        projectDir2Project = new HashMap<File, Project>();
    }

    public void addProject(Project project) {
        if (projectDir2Project.get(project.getProjectDir()) != null) {
            throw new InvalidUserDataException("Project " + project + " has already existing projectDir: " + project.getProjectDir());
        }
        projects.put(project.getPath(), project);
        subProjects.put(project.getPath(), new TreeSet());
        addProjectToParentSubProjects(project);
        projectDir2Project.put(project.getProjectDir(), project);
    }

    private void addProjectToParentSubProjects(Project project) {
        Project loopProject = project.getParent();
        while (loopProject != null) {
            subProjects.get(loopProject.getPath()).add(project);
            loopProject = loopProject.getParent();
        }
    }

    public Project getProject(String path) {
        return projects.get(path);
    }

    public Project getProject(File projectDir) {
        return projectDir2Project.get(projectDir);
    }

    public Set<Project> getAllProjects(String path) {
        Set<Project> result = new TreeSet(getSubProjects(path));
        if (projects.get(path) != null) {
            result.add(projects.get(path));
        }
        return result;
    }

    public Set<Project> getSubProjects(String path) {
        return GUtil.elvis(subProjects.get(path), new TreeSet<Project>());
    }

    public void reset() {
        init();
    }
}
