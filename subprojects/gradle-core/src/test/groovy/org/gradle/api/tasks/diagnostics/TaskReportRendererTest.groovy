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
import org.gradle.api.Rule

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
        String rule1Description = "rule1Description"
        String rule2Description = "rule2Description"
        Rule rule1 = [getDescription: {rule1Description}] as Rule
        Rule rule2 = [getDescription: {rule2Description}] as Rule

        List testDefaultTasks = ['task1', 'task2']
        renderer.addDefaultTasks(testDefaultTasks)
        renderer.startTaskGroup('group')
        renderer.addTask(task1)
        renderer.addTask(task2)
        renderer.completeTasks()
        renderer.addRule(rule1)
        renderer.addRule(rule2)

        def expected = '''Default tasks: task1, task2

Group tasks
-----------
:task1 - task1Description
   -> :task11, :task12
:task2

Rules
-----
rule1Description
rule2Description
'''
        assertEquals(expected, writer.toString())
    }

    @Test public void testWritesTaskAndDependenciesWithNoDefaultTasks() {
        TaskDependency taskDependency = [getDependencies: {[] as Set}] as TaskDependency
        Task task = [getPath: {':task1'}, getDescription: {null}, getTaskDependencies: {taskDependency}] as Task
        renderer.addDefaultTasks([])
        renderer.startTaskGroup('group')
        renderer.addTask(task)

        def expected = '''Group tasks
-----------
:task1
'''
        assertEquals(expected, writer.toString())
    }

    @Test public void testProjectWithNoTasksAndNoRules() {
        renderer.completeTasks()

        def expected = '''No tasks
'''
        assertEquals(expected, writer.toString())
    }

    @Test public void testProjectWithNoTasksButRules() {
        String ruleDescription = "someDescription"

        renderer.completeTasks()
        renderer.addRule([getDescription: {ruleDescription}] as Rule)

        def expected = '''No tasks

Rules
-----
someDescription
'''
        assertEquals(expected, writer.toString())
    }
}
