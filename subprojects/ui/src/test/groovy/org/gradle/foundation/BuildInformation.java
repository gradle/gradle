/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation;

import org.gradle.api.Project;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This provides a simple way to hold onto and obtain projects and tasks for testing purposes.
 */
public class BuildInformation {
    private List<ProjectView> projects;

    public BuildInformation(Project rootProject) {
        ProjectConverter buildExecuter = new ProjectConverter();
        projects = buildExecuter.convertProjects(rootProject);
    }

    public List<ProjectView> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    /*
       Call this to get the root level project with the given name.
       @param  name       the name of the project.
       @return the project if it exists.
    */

    public ProjectView getRootLevelProject(String name) {
        if (name == null) {
            return null;
        }

        Iterator<ProjectView> iterator = projects.iterator();
        while (iterator.hasNext()) {
            ProjectView projectView = iterator.next();
            if (name.equals(projectView.getName())) {
                return projectView;
            }
        }

        return null;
    }

    /*
       Locates the project that has the specified full path
       @param  fullProjectPath the full path of the sought project.
       @return a project or null.
    */

    public ProjectView getProjectFromFullPath(String fullProjectPath) {
        if (projects.isEmpty()) {
            return null;
        }   //we haven't loaded yet

        PathParserPortion pathParserPortion = new PathParserPortion(fullProjectPath);
        if (pathParserPortion.getFirstPart() == null) {
            return null;
        }

        ProjectView rootProject = getRootLevelProject(pathParserPortion.getFirstPart());
        if (rootProject
                == null)  //if the root wasn't specified, just go get the first item we have. root' isn't typically specified if a user gives us the path.
        {
            if (!projects.isEmpty()) {
                rootProject = projects.get(0);
            }
        }

        if (rootProject == null) {
            return null;
        }

        if (!pathParserPortion.hasRemainder()) {
            return rootProject;
        }

        return rootProject.getSubProjectFromFullPath(pathParserPortion.getRemainder());
    }

    /*
       This gets the task based on the given full path. The root is a little
       special.

       @param  fullTaskName the full task name (root_project:sub_project:sub_sub_project:task.).
       @return the task or null if not found.
    */

    public TaskView getTaskFromFullPath(String fullTaskName) {
        if (projects.isEmpty()) {
            return null;
        }   //we haven't loaded yet

        PathParserPortion pathParserPortion = new PathParserPortion(fullTaskName);
        if (pathParserPortion.getFirstPart() == null) {
            return null;
        }

        String remainder = pathParserPortion.getRemainder();
        ProjectView rootProject = null;
        if (pathParserPortion.getFirstPart().equals(""))   //this means it starts with a colon, just get the root
        {
            if (!projects.isEmpty()) {
                rootProject = projects.get(0);
            }
        } else {  //see if they did specify the root.
            rootProject = getRootLevelProject(pathParserPortion.getFirstPart());
            if (rootProject
                    == null)  //if the root wasn't specified, just go get the first item we have. root' isn't typically specified if a user gives us the path.
            {
                if (!projects.isEmpty()) {
                    rootProject = projects.get(0);
                    remainder = fullTaskName;
                }
            }
        }

        //we found a match. We only want whatever's left
        if (rootProject != null) {
            return rootProject.getTaskFromFullPath(remainder);
        }

        //we don't have a full path.
        return null;
    }
}
