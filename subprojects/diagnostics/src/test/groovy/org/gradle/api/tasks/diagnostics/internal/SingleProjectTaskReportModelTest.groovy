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

import org.gradle.api.Task
import org.gradle.util.Path

class SingleProjectTaskReportModelTest extends AbstractTaskModelSpec {
    final TaskDetailsFactory factory = Mock()

    def setup() {
        _ * factory.create(!null) >> {args ->
            def task = args[0]
            [getPath: { Path.path(task.path) }, getName: { task.name }] as TaskDetails
        }
    }

    def groupsTasksBasedOnTheirGroup() {
        def task1 = task('task1', 'group1')
        def task2 = task('task2', 'group2')
        def task3 = task('task3', 'group1')

        when:
        def model = modelFor([task1, task2, task3])

        then:
        model.groups == ['group1', 'group2'] as Set
        model.getTasksForGroup('group1')*.path == [pathOf(task1), pathOf(task3)]
        model.getTasksForGroup('group2')*.path == [pathOf(task2)]
    }

    def groupsAreTreatedAsCaseInsensitive() {
        def task1 = task('task1', 'a')
        def task2 = task('task2', 'B')
        def task3 = task('task3', 'b')
        def task4 = task('task4', 'c')

        when:
        def model = modelFor([task2, task3, task4, task1])

        then:
        model.groups == ['a', 'B', 'c'] as Set
        model.getTasksForGroup('a')*.path == [pathOf(task1)]
        model.getTasksForGroup('B')*.path == [pathOf(task2), pathOf(task3)]
        model.getTasksForGroup('c')*.path == [pathOf(task4)]
    }

    def tasksWithNoGroupAreTreatedAsChildrenOfTheNearestTopLevelTaskTheyAreReachableFrom() {
        def task1 = task('task1')
        def task2 = task('task2')
        def task3 = task('task3', 'group1', task1, task2)
        def task4 = task('task4', task3, task1)
        def task5 = task('task5', 'group2', task4)

        when:
        def model = modelFor([task1, task2, task3, task4, task5])

        then:
        TaskDetails task3Details = (model.getTasksForGroup('group1') as List).first()
        task3Details.path == pathOf(task3)

        TaskDetails task5Details = (model.getTasksForGroup('group2') as List).first()
        task5Details.path == pathOf(task5)
    }

    def addsAGroupThatContainsTheTasksWithNoGroup() {
        def task1 = task('task1')
        def task2 = task('task2', 'group', task1)
        def task3 = task('task3')
        def task4 = task('task4', task2)
        def task5 = task('task5', task3, task4)

        when:
        def model = modelFor([task1, task2, task3, task4, task5])

        then:
        model.groups == ['group', ''] as Set
        def tasks = model.getTasksForGroup('') as List
        tasks*.path == [pathOf(task1), pathOf(task3), pathOf(task4), pathOf(task5)]
        def t = tasks.first()
        t.path == pathOf(task1)
    }

    def addsAGroupWhenThereAreNoTasksWithAGroup() {
        def task1 = task('task1')
        def task2 = task('task2', task1)
        def task3 = task('task3')

        when:
        def model = modelFor([task1, task2, task3])

        then:
        model.groups == [''] as Set
        def tasks = model.getTasksForGroup('') as List
        tasks*.path == [pathOf(task1), pathOf(task2), pathOf(task3)]
    }

    def buildsModelWhenThereAreNoTasks() {
        when:
        def model = modelFor([])

        then:
        model.groups as List == []
    }

    private SingleProjectTaskReportModel modelFor(List<Task> tasks) {
        SingleProjectTaskReportModel.forTasks(tasks, factory)
    }

    private static Path pathOf(Task t) {
        Path.path(t.path)
    }
}
