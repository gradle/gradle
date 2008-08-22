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

package org.gradle.configuration

import org.gradle.api.Project
import org.gradle.api.ProjectAction
import org.gradle.util.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.internal.project.ProjectInternal

/**
 * @author Hans Dockter
 */
class BuildConfigurer {
    private static Logger logger = LoggerFactory.getLogger(BuildConfigurer)

    ProjectDependencies2TaskResolver projectDependencies2TasksResolver

    ProjectTasksPrettyPrinter projectTasksPrettyPrinter

    ProjectAction projectEvaluateAction

    BuildConfigurer() {}

    BuildConfigurer(ProjectDependencies2TaskResolver projectDependencies2TasksResolver, ProjectTasksPrettyPrinter projectTasksPrettyPrinter) {
        this.projectDependencies2TasksResolver = projectDependencies2TasksResolver
        this.projectTasksPrettyPrinter = projectTasksPrettyPrinter
        projectEvaluateAction = {ProjectInternal project ->
            project.evaluate()
        } as ProjectAction
    }

    void process(Project rootProject) {
        logger.info('++ Configuring Project objects')
        Clock clock = new Clock()
        rootProject.allprojects(projectEvaluateAction)
        projectDependencies2TasksResolver.resolve(rootProject)
        logger.debug("Timing: Configuring projects took " + clock.time)
    }

    String taskList(Project rootProject, boolean recursive, Project currentProject) {
        assert rootProject
        assert currentProject

        process(rootProject)

        logger.debug("Finding tasks for project: {} Recursive:{}", currentProject, recursive)
        Map tasks = currentProject.getAllTasks(recursive)
        projectTasksPrettyPrinter.getPrettyText(tasks)
    }

}