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

import java.util.*;

/**
 * @author Hans Dockter
 */
public class ProjectRegistry {
    private Map<String, Project> projects = new HashMap<String, Project>();

    private Map<String, Set<Project>> subProjects = new HashMap<String, Set<Project>>();

    public void addProject(AbstractProject project) {
        projects.put(project.getPath(), project);
        subProjects.put(project.getPath(), new TreeSet());
        Project loopProject = project.getParent();
        while (loopProject != null) {
            subProjects.get(loopProject.getPath()).add(project);
            loopProject = loopProject.getParent();
        }
    }

    public Project getProject(String path) {
        return projects.get(path);
    }

    public Set<Project> getAllProjects(String path) {
        Set<Project> result = new TreeSet(getSubProjects(path));
        result.add(projects.get(path));
        return result;
    }

    public Set<Project> getSubProjects(String path) {
        return subProjects.get(path);
    }
}
