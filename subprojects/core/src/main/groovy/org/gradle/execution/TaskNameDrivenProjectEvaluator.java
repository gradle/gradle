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

package org.gradle.execution;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.NameMatcher;

import java.util.List;
import java.util.Map;

/**
 * Ensures that necessary projects are evaluated. Has no effect if
 *
 * by Szczepan Faber, created at: 11/22/12
 */
public class TaskNameDrivenProjectEvaluator implements BuildConfigurationAction {

    public void configure(BuildExecutionContext context) {
        if (!context.getGradle().getStartParameter().isConfigureOnDemand()) {
            context.proceed();
            return;
        }

        List<String> taskNames = context.getGradle().getStartParameter().getTaskNames();
        ProjectInternal project = context.getGradle().getDefaultProject();

        //TODO SF most below is copied over from the TaskSelector - it needs refactoring, coverage, etc.
        //Probably this action should be only configured when configuration on demand is 'on'
        for (String path : taskNames) {
            if (path.contains(Project.PATH_SEPARATOR)) {
                String projectPath = StringUtils.substringBeforeLast(path, Project.PATH_SEPARATOR);
                projectPath = projectPath.length() == 0 ? Project.PATH_SEPARATOR : projectPath;
                project = findProject(project, projectPath);
                project.evaluate();
            } else {
                project.evaluate();
                for (Project sub : project.getSubprojects()) {
                    ((ProjectInternal) sub).evaluate();
                }
            }
        }
        context.proceed();
    }

    //from TaskSelector
    private static ProjectInternal findProject(ProjectInternal startFrom, String path) {
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

            throw new TaskSelectionException(matcher.formatErrorMessage("project", current));
        }

        return (ProjectInternal) current;
    }
}
