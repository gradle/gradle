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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import static org.gradle.util.Matchers.*
import org.junit.Test

/**
 * @author Hans Dockter
 */
class TaskReportRendererTest {
    private final StringWriter writer = new StringWriter()
    private final TaskReportRenderer renderer = new TaskReportRenderer(writer)

    @Test public void testWritesTaskAndDependencies() {
        Task task11 = [getPath: {':task11'}] as Task
        Task task12 = [getPath: {':task12'}, getDescription: {null}] as Task
        TaskDependency taskDependency1 = [getDependencies: {[task11, task12] as Set}] as TaskDependency
        TaskDependency taskDependency2 = [getDependencies: {[] as Set}] as TaskDependency
        String task1Description = 'task1Description'
        Task task1 = [getPath: {':task1'}, getDescription: {task1Description}, getTaskDependencies: {taskDependency1}] as Task
        Task task2 = [getPath: {':task2'}, getDescription: {null}, getTaskDependencies: {taskDependency2}] as Task

        List testDefaultTasks = ['task1, task2']
        renderer.addDefaultTasks(testDefaultTasks)
        renderer.addTask(task1)
        renderer.addTask(task2)

        List lines = new StringReader(writer.toString()).readLines()
        assertThat(lines[0], containsString("Default Tasks: " + testDefaultTasks.join(', ')))
        assertThat(lines[2], containsString(":task1 - $task1Description"))
        assertThat(lines[3], containsString("-> :task11, :task12"))
        assertThat(lines[4], containsString(":task2"))
        assertThat(lines[4], not(containsString(":task2 -")))
        assertThat(lines.size(), equalTo(5))
    }

    @Test public void testWritesTaskAndDependenciesWithNoDefaultTasks() {
        TaskDependency taskDependency = [getDependencies: {[] as Set}] as TaskDependency
        Task task = [getPath: {':task1'}, getDescription: {null}, getTaskDependencies: {taskDependency}] as Task
        renderer.addDefaultTasks([])
        renderer.addTask(task)

        List lines = new StringReader(writer.toString()).readLines()
        assertThat(lines[0], containsString(":task"))
        assertThat(lines.size(), equalTo(1))
    }

    @Test public void testProjectWithNoTasks() {
        Project project = [getPath: {':project'}, getRootProject: {null}] as Project

        renderer.startProject(project)
        renderer.completeProject(project)

        assertThat(writer.toString(), containsLine('No tasks'))
    }
    
    @Test public void testProjectWithTasks() {
        TaskDependency taskDependency = [getDependencies: {[] as Set}] as TaskDependency
        Task task = [getPath: {':task'}, getDescription: {null}, getTaskDependencies: {taskDependency}] as Task
        Project project = [getPath: {':project'}, getRootProject: {null}] as Project

        renderer.startProject(project)
        renderer.addTask(task)
        renderer.completeProject(project)

        assertThat(writer.toString(), not(containsString('No tasks')))
    }
}
