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

import org.gradle.util.Path

class SingleProjectTaskReportModelTest extends AbstractTaskModelSpec {
    final TaskDetailsFactory factory = Mock()
    final SingleProjectTaskReportModel model = new SingleProjectTaskReportModel(factory)

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
        model.build([task1, task2, task3])

        then:
        model.groups == ['group1', 'group2'] as Set
        model.getTasksForGroup('group1')*.task == [task1, task3]
        model.getTasksForGroup('group2')*.task == [task2]
    }

    def groupsAreTreatedAsCaseInsensitive() {
        def task1 = task('task1', 'a')
        def task2 = task('task2', 'B')
        def task3 = task('task3', 'b')
        def task4 = task('task4', 'c')

        when:
        model.build([task2, task3, task4, task1])

        then:
        model.groups == ['a', 'B', 'c'] as Set
        model.getTasksForGroup('a')*.task == [task1]
        model.getTasksForGroup('B')*.task == [task2, task3]
        model.getTasksForGroup('c')*.task == [task4]
    }

    def tasksWithNoGroupAreTreatedAsChildrenOfTheNearestTopLevelTaskTheyAreReachableFrom() {
        def task1 = task('task1')
        def task2 = task('task2')
        def task3 = task('task3', 'group1', task1, task2)
        def task4 = task('task4', task3, task1)
        def task5 = task('task5', 'group2', task4)

        when:
        model.build([task1, task2, task3, task4, task5])

        then:
        TaskDetails task3Details = (model.getTasksForGroup('group1') as List).first()
        task3Details.task == task3
        task3Details.children*.task == [task1, task2]

        TaskDetails task5Details = (model.getTasksForGroup('group2') as List).first()
        task5Details.task == task5
        task5Details.children*.task == [task4]
    }

    def theDependenciesOfATopLevelTaskAreTheUnionOfTheDependenciesOfItsChildren() {
        def task1 = task('task1', 'group1')
        def task2 = task('task2', 'group2', task1)
        def task3 = task('task3', 'group3')
        def task4 = task('task4', task2)
        def task5 = task('task5', 'group4', task3, task4)

        when:
        model.build([task1, task2, task3, task4, task5])

        then:
        TaskDetails t = (model.getTasksForGroup('group4') as List).first()
        t.dependencies*.path*.name as Set == ['task2', 'task3'] as Set
    }

    def dependenciesIncludeExternalTasks() {
        def task1 = task('task1')
        def task2 = task('task2', 'other')
        def task3 = task('task3', 'group', task1, task2)

        when:
        model.build([task2, task3])

        then:
        TaskDetails t = (model.getTasksForGroup('group') as List).first()
        t.dependencies*.path*.name as Set == ['task1', 'task2'] as Set
    }

    def dependenciesDoNotIncludeTheChildrenOfOtherTopLevelTasks() {
        def task1 = task('task1')
        def task2 = task('task2', 'group1', task1)
        def task3 = task('task3', task1)
        def task4 = task('task4', task2)
        def task5 = task('task5', 'group2', task3, task4)

        when:
        model.build([task1, task2, task3, task4, task5])

        then:
        TaskDetails t = (model.getTasksForGroup('group2') as List).first()
        t.dependencies*.path*.name as Set == ['task2'] as Set
    }

    def addsAGroupThatContainsTheTasksWithNoGroup() {
        def task1 = task('task1')
        def task2 = task('task2', 'group', task1)
        def task3 = task('task3')
        def task4 = task('task4', task2)
        def task5 = task('task5', task3, task4)

        when:
        model.build([task1, task2, task3, task4, task5])

        then:
        model.groups == ['group', ''] as Set
        def tasks = model.getTasksForGroup('') as List
        tasks*.task == [task5]
        def t = tasks.first()
        t.task == task5
        t.children*.task == [task3, task4]
    }

    def addsAGroupWhenThereAreNoTasksWithAGroup() {
        def task1 = task('task1')
        def task2 = task('task2', task1)
        def task3 = task('task3')

        when:
        model.build([task1, task2, task3])

        then:
        model.groups == [''] as Set
        def tasks = model.getTasksForGroup('') as List
        tasks*.task == [task2, task3]
    }

    def buildsModelWhenThereAreNoTasks() {
        when:
        model.build([])

        then:
        model.groups as List == []
    }

    def ignoresReachableTasksOutsideTheProject() {
        def other1 = task('other1')
        def other2 = task('other2', other1)
        def task1 = task('task1', other2)
        def task2 = task('task2', 'group1', task1)

        when:
        model.build([task1, task2])

        then:
        TaskDetails t = (model.getTasksForGroup('group1') as List).first()
        t.children*.task == [task1]
        t.dependencies*.path*.name == ['other2']
    }
}
