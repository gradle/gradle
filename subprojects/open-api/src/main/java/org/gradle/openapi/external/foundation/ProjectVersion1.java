/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.external.foundation;

import java.io.File;
import java.util.List;

/**
 * This is an abstraction of a Gradle project
 *
 * This is a mirror of ProjectView inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface ProjectVersion1 {

    /**
     * @return the name of this project
     */
    public String getName();

    /**
     * @return The full project name. This is just the project name if its off of the root. Otherwise, its all of its ancestors separated by colons with this project being last.
     */
    public String getFullProjectName();

    /**
     * @return the TaskVersion1 objects associated with this project
     */
    public List<TaskVersion1> getTasks();

    /**
     * @return the .gradle file this project is defined in
     */
    public File getFile();

    /**
     * @return the sub projects of this project
     */
    public List<ProjectVersion1> getSubProjects();

    /**
     * @return the parent of this project if this is a sub project. Otherwise, null
     */
    public ProjectVersion1 getParentProject();

    /**
     * @return a list of projects that this project depends on.
     */
    public List<ProjectVersion1> getDependantProjects();

    public ProjectVersion1 getSubProject(String name);

    public ProjectVersion1 getSubProjectFromFullPath(String fullProjectName);

    public TaskVersion1 getTask(String name);

    /**
     * Builds a list of default tasks. These are defined by specifying
     *
     * defaultTasks 'task name'
     *
     * in the gradle file. There can be multiple default tasks. This only returns default tasks directly for this project and does not return them for subprojects.
     *
     * @return a list of default tasks or an empty list if none exist
     */
    public List<TaskVersion1> getDefaultTasks();

    public TaskVersion1 getTaskFromFullPath(String fullTaskName);
}
