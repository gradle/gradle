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
import org.junit.Test

/**
 * @author Hans Dockter
 */
class TaskReportRendererTest {
    StringWriter writer = new StringWriter()
    TaskReportRenderer renderer = new TaskReportRenderer(writer)

    @Test public void testWritesTaskAndDependencies() {
        Task task1 = [getPath: {':task1'}] as Task
        Task task2 = [getPath: {':task2'}] as Task
        TaskDependency taskDependency = [getDependencies: {[task1, task2] as Set}] as TaskDependency
        Task task = [getPath: {':task'}, getTaskDependencies: {taskDependency}] as Task

        renderer.addTask(task)

        assertThat(writer.toString(), containsString('Task :task [:task1, :task2]'))
    }

    @Test public void testProjectWithNoTasks() {
        Project project = [getPath: {':project'}] as Project

        renderer.startProject(project)
        renderer.completeProject(project)

        assertThat(writer.toString(), containsString('No tasks'))
    }
    
    @Test public void testProjectWithTasks() {
        TaskDependency taskDependency = [getDependencies: {[] as Set}] as TaskDependency
        Task task = [getPath: {':task'}, getTaskDependencies: {taskDependency}] as Task
        Project project = [getPath: {':project'}] as Project

        renderer.startProject(project)
        renderer.addTask(task)
        renderer.completeProject(project)

        assertThat(writer.toString(), not(containsString('No tasks')))
    }
}
