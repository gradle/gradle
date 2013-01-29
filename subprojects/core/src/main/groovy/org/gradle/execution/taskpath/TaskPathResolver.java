/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;

/**
 * by Szczepan Faber, created at: 1/29/13
 */
public class TaskPathResolver {

    private final ProjectFinderByTaskPath projectFinder;

    TaskPathResolver(ProjectFinderByTaskPath projectFinder) {
        this.projectFinder = projectFinder;
    }

    public TaskPathResolver() {
        this(new ProjectFinderByTaskPath());
    }

    /**
     * @param path the task path, e.g. 'someTask', 'sT', ':sT', ':foo:bar:sT'
     * @param startFrom the starting project the task should be found recursively
     * @return resolved task path
     */
    public ResolvedTaskPath resolvePath(String path, ProjectInternal startFrom) {
        ProjectInternal project;
        String taskName; //eg. 'someTask' or 'sT'
        String prefix; //eg. '', ':' or ':foo:bar'

        if (ResolvedTaskPath.isQualified(path)) {
            project = projectFinder.findProject(path, startFrom);
            taskName = StringUtils.substringAfterLast(path, Project.PATH_SEPARATOR);
            prefix = project.getPath() + Project.PATH_SEPARATOR;
        } else {
            project = startFrom;
            taskName = path;
            prefix = "";
        }
        return new ResolvedTaskPath(path, prefix, taskName, project);
    }
}