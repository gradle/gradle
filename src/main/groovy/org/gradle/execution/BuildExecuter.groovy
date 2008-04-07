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

package org.gradle.execution

import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.project.DefaultProject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class BuildExecuter {
    private static Logger logger = LoggerFactory.getLogger(BuildExecuter)

    Dag dag

    BuildExecuter() {}
    BuildExecuter(Dag dag) {
        this.dag = dag
    }

    void execute(String taskName, boolean recursive, DefaultProject currentProject, DefaultProject rootProject) {
        assert taskName
        assert currentProject

        logger.info("++ Executing: $taskName Recursive:$recursive Startproject: $currentProject")

        dag.reset()
        Map<DefaultProject, DefaultTask> calledTasks = currentProject.getTasksByName(taskName, recursive)
        logger.debug("Found tasks: ${calledTasks.values()}")
        if (!calledTasks) {
            throw new UnknownTaskException("No tasks available for $taskName!")
        }
        Dag dag = fillDag(dag, calledTasks.values(), rootProject)
        dag.projects.each {Project project ->
            if (project.configureByDag) {project.configureByDag.call(dag)}
        }
        dag.getAllTasks().each {
            it.applyAfterDagClosures()
        }
        dag.execute()
    }

    List unknownTasks(List taskNames, boolean recursive, DefaultProject currentProject) {
        taskNames.findAll {String taskName ->
            Map<DefaultProject, DefaultTask> calledTasks = currentProject.getTasksByName(taskName, recursive)
            !calledTasks.values().collect {it.name}.contains(taskName)
        }
    }

    private Dag fillDag(Dag dag, Collection tasks, DefaultProject rootProject) {
        tasks.each {DefaultTask task ->
            Set dependsOnTasks = findDependsOnTasks(task, rootProject)
            dag.addTask(task, dependsOnTasks)
            if (dependsOnTasks) {
                logger.debug("Found dependsOn tasks for $task: $dependsOnTasks")
                fillDag(dag, dependsOnTasks, rootProject)
            } else {
                logger.debug("Found no dependsOn tasks for $task")
            }
        }
        dag
    }

    private List findDependsOnTasks(DefaultTask task, DefaultProject rootProject) {
        logger.debug("Find dependsOn tasks for $task")
        task.dependsOn.collect {String taskPath ->
            String absolutePath = task.project.absolutePath(taskPath)
            DefaultProject project = getProjectFromTaskPath(absolutePath, rootProject)
            String dependsOnTaskName = absolutePath - project.path
            if (!project.is(project.rootProject)) {dependsOnTaskName = dependsOnTaskName.substring(1)}
            DefaultTask dependsOnTask = project.tasks[dependsOnTaskName]
            if (!dependsOnTask) throw new UnknownTaskException("Task with path $taskPath could not be found.")
            dependsOnTask
        }
    }

    private DefaultProject getProjectFromTaskPath(String taskPath, DefaultProject rootProject) {
        logger.debug("Get project by task path: $taskPath")
        assert DefaultProject.isAbsolutePath(taskPath)
        List pathList = taskPath.substring(1).split(Project.PATH_SEPARATOR)
        try {
            return DefaultProject.findProject(rootProject, Project.PATH_SEPARATOR + pathList[0..<pathList.size() - 1].join(Project.PATH_SEPARATOR))
        } catch (UnknownProjectException e) {
            throw new UnknownTaskException("Task with path $taskPath could not be found!")
        }
    }

}