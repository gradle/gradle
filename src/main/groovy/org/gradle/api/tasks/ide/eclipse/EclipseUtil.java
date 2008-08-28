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
package org.gradle.api.tasks.ide.eclipse;

import org.gradle.api.Project;
import org.gradle.api.dependencies.ProjectDependency;

import java.util.*;

/**
 * @author Hans Dockter
 */
class EclipseUtil {
    static Set<Project> getDependsOnProjects(List<ProjectDependency> projectDependencies) {
        Set<Project> dependsOnProjects = new HashSet<Project>();
        for (ProjectDependency projectDependency : projectDependencies) {
            dependsOnProjects.add(projectDependency.getDependencyProject());
        }
        return dependsOnProjects;
    }

    static List<String> getSortedStringList(List pathList) {
        List<String> sortedList = new ArrayList<String>();
        for (Object path : pathList) {
            sortedList.add(path.toString());
        }
        Collections.sort(sortedList);
        return sortedList;
    }
}
