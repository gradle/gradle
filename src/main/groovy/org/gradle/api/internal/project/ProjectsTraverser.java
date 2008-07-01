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

import groovy.lang.Closure;

import java.util.Collection;
import java.util.TreeSet;

import org.gradle.api.Project;
import org.gradle.api.ProjectAction;

/**
 * @author Hans Dockter
 */
public class ProjectsTraverser {
    public void traverse(Collection<Project> projects, ProjectAction action) {
        projects = new TreeSet(projects);
        if (projects.size() == 0) return;
        for (Project project : projects) {
            action.execute(project);    
        }
        for (Project project : projects) {
            traverse(project.getChildProjects().values(), action);    
        }
    }
}