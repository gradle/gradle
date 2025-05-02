/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal

import org.gradle.internal.logging.text.TestStyledTextOutput

class TaskReportRendererTest extends AbstractTaskModelSpec {
    private final TestStyledTextOutput writer = new TestStyledTextOutput().ignoreStyle()
    private final TaskReportRenderer renderer = new TaskReportRenderer()

    def setup() {
        renderer.output = writer
    }

    def writesTasksWithDetailDisabled() {
        TaskDetails task1 = taskDetails(':task1', description: 'task1Description')
        taskDetails(':task2')
        TaskDetails task3 = taskDetails(':task3')
        RuleDetails rule1 = [getDescription: {'rule1Description'}] as RuleDetails
        RuleDetails rule2 = [getDescription: {'rule2Description'}] as RuleDetails

        List testDefaultTasks = ['task1', 'task2']

        when:
        renderer.showDetail(false)
        renderer.addDefaultTasks(testDefaultTasks)
        renderer.startTaskGroup('group')
        renderer.addTask(task1)
        renderer.addTask(task3)
        renderer.completeTasks()
        renderer.addRule(rule1)
        renderer.addRule(rule2)

        then:
        writer.value == '''Default tasks: task1, task2

Group tasks
-----------
:task1 - task1Description
:task3

Rules
-----
rule1Description
rule2Description
'''
    }

    def writesTasksWithDetail() {
        TaskDetails task11 = taskDetails(':task11')
        TaskDetails task12 = taskDetails(':task12')
        TaskDetails task1 = taskDetails(':task1', description: 'task1Description', dependencies: [task11, task12])
        TaskDetails task2 = taskDetails(':task2')
        TaskDetails task3 = taskDetails(':task3', dependencies: [task1])
        RuleDetails rule1 = [getDescription: {'rule1Description'}] as RuleDetails
        RuleDetails rule2 = [getDescription: {'rule2Description'}] as RuleDetails
        List testDefaultTasks = ['task1', 'task2']

        when:
        renderer.showDetail(true)
        renderer.addDefaultTasks(testDefaultTasks)
        renderer.startTaskGroup('group')
        renderer.addTask(task1)
        renderer.addTask(task2)
        renderer.addTask(task3)
        renderer.completeTasks()
        renderer.addRule(rule1)
        renderer.addRule(rule2)

        then:
        writer.value == '''Default tasks: task1, task2

Group tasks
-----------
:task1 - task1Description
:task2
:task3

Rules
-----
rule1Description
rule2Description
'''
    }

    def writesTasksForSingleGroup() {
        TaskDetails task = taskDetails(':task1')

        when:
        renderer.addDefaultTasks([])
        renderer.startTaskGroup('group')
        renderer.addTask(task)
        renderer.completeTasks()

        then:
        writer.value == '''Group tasks
-----------
:task1
'''
    }

    def writesTasksForMultipleGroups() {
        TaskDetails task = taskDetails(':task1')
        TaskDetails task2 = taskDetails(':task2')

        when:
        renderer.addDefaultTasks([])
        renderer.startTaskGroup('group')
        renderer.addTask(task)
        renderer.startTaskGroup('other')
        renderer.addTask(task2)
        renderer.completeTasks()

        then:
        writer.value == '''Group tasks
-----------
:task1

Other tasks
-----------
:task2
'''
    }

    def writesTasksForDefaultGroup() {
        TaskDetails task = taskDetails(':task1')

        when:
        renderer.addDefaultTasks([])
        renderer.startTaskGroup('')
        renderer.addTask(task)
        renderer.completeTasks()

        then:
        writer.value == '''Tasks
-----
:task1
'''
    }

    def writesProjectWithNoTasksAndNoRules() {
        when:
        renderer.completeTasks()

        then:
        writer.value == '''No tasks
'''
    }

    def writesProjectWithRulesAndNoTasks() {
        String ruleDescription = "someDescription"

        when:
        renderer.completeTasks()
        renderer.addRule([getDescription: {ruleDescription}] as RuleDetails)

        then:
        writer.value == '''No tasks

Rules
-----
someDescription
'''
    }
}
