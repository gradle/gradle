/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.taskpath;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.NameMatcher;

import java.util.Map;

/**
 * by Szczepan Faber, created at: 1/3/13
 */
public class ProjectFinderByTaskPath {

    public ProjectInternal findProject(String taskPath, ProjectInternal startFrom) {
        if (!taskPath.contains(Project.PATH_SEPARATOR)) {
            throw new IllegalArgumentException("I can only find tasks based on a task path (e.g. containing ':'). However, '" + taskPath + "' was passed.");
        }
        String projectPath = StringUtils.substringBeforeLast(taskPath, Project.PATH_SEPARATOR);
        projectPath = projectPath.length() == 0 ? Project.PATH_SEPARATOR : projectPath;
        return findProjectNow(projectPath, startFrom);
    }

    private static ProjectInternal findProjectNow(String path, ProjectInternal startFrom) {
        if (path.equals(Project.PATH_SEPARATOR)) {
            return startFrom.getRootProject();
        }
        Project current = startFrom;
        if (path.startsWith(Project.PATH_SEPARATOR)) {
            current = current.getRootProject();
            path = path.substring(1);
        }
        for (String pattern : path.split(Project.PATH_SEPARATOR)) {
            Map<String, Project> children = current.getChildProjects();

            NameMatcher matcher = new NameMatcher();
            Project child = matcher.find(pattern, children);
            if (child != null) {
                current = child;
                continue;
            }

            throw new ProjectLookupException(matcher.formatErrorMessage("project", current));
        }

        return (ProjectInternal) current;
    }

    public static class ProjectLookupException extends InvalidUserDataException {
        public ProjectLookupException(String message) {
            super(message);
        }
    }
}