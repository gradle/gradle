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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.ProjectsTraverser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.util.Clock

/**
* @author Hans Dockter
*/
class BuildConfigurer {
    private static Logger logger = LoggerFactory.getLogger(BuildConfigurer)

    BuildClasspathLoader buildClasspathLoader

    ProjectDependencies2TasksResolver projectDependencies2TasksResolver

    ProjectsTraverser projectsTraverser

    ProjectTasksPrettyPrinter projectTasksPrettyPrinter

    BuildConfigurer() {}

    BuildConfigurer(ProjectDependencies2TasksResolver projectDependencies2TasksResolver, BuildClasspathLoader buildClasspathLoader, ProjectsTraverser projectsTraverser, ProjectTasksPrettyPrinter projectTasksPrettyPrinter) {
        this.projectDependencies2TasksResolver = projectDependencies2TasksResolver
        this.buildClasspathLoader = buildClasspathLoader
        this.projectsTraverser = projectsTraverser
        this.projectTasksPrettyPrinter = projectTasksPrettyPrinter
    }

    void process(Project rootProject, ClassLoader classLoader) {
        logger.info('++ Configuring Project objects')
        Clock clock = new Clock()
        rootProject.buildScriptProcessor.classLoader = classLoader
        projectsTraverser.traverse([rootProject]) {DefaultProject project ->
            project.evaluate()
        }
        projectDependencies2TasksResolver.resolve(rootProject)
        logger.debug("Timing: Configuring projects took " + clock.time)
    }

    String taskList(Project rootProject, boolean recursive, Project currentProject, ClassLoader classLoader) {
        assert rootProject
        assert currentProject

        process(rootProject, classLoader)

        logger.debug("Finding tasks for project: $currentProject Recursive:$recursive")
        Map tasks = currentProject.getAllTasks(recursive)
        projectTasksPrettyPrinter.getPrettyText(tasks)
    }

}