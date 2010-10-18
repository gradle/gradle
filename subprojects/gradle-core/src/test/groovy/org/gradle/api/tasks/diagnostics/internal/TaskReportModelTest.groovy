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

import spock.lang.Specification
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency

class TaskReportModelTest extends Specification {
    private final TaskReportModel model = new TaskReportModel()

    def groupsTasksBasedOnTheirGroup() {
        def task1 = task('task1', 'group1')
        def task2 = task('task2', 'group2')
        def task3 = task('task3', 'group1')

        when:
        model.calculate([task1, task2, task3])

        then:
        model.groups as List == ['group1', 'group2']
        model.getTasksForGroup('group1')*.task == [task1, task3]
        model.getTasksForGroup('group2')*.task == [task2]
    }

    def groupsAreTreatedAsCaseInsensitive() {
        def task1 = task('task1', 'a')
        def task2 = task('task2', 'B')
        def task3 = task('task3', 'b')
        def task4 = task('task4', 'c')

        when:
        model.calculate([task2, task3, task4, task1])

        then:
        model.groups as List == ['a', 'B', 'c']
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
        model.calculate([task1, task2, task3, task4, task5])

        then:
        TaskDetails t = (model.getTasksForGroup('group1') as List).first()
        t.task == task3
        t.children*.task as List == [task1, task2]
        t = (model.getTasksForGroup('group2') as List).first()
        t.task == task5
        t.children*.task as List == [task4]
    }

    def theDependenciesOfATopLevelTaskAreTheUnionOfTheDependenciesOfItsChildren() {
        def task1 = task('task1', 'group1')
        def task2 = task('task2', 'group2', task1)
        def task3 = task('task3', 'group3')
        def task4 = task('task4', task2)
        def task5 = task('task5', 'group4', task3, task4)

        when:
        model.calculate([task1, task2, task3, task4, task5])

        then:
        TaskDetails t = (model.getTasksForGroup('group4') as List).first()
        t.dependencies as List == ['task2', 'task3']
    }

    def usesTaskPathForExternalDependencies() {
        def task1 = task('task1')
        def task2 = task('task2', 'other')
        def task3 = task('task3', 'group', task1, task2)

        when:
        model.calculate([task2, task3])

        then:
        TaskDetails t = (model.getTasksForGroup('group') as List).first()
        t.dependencies as List == [':task1', 'task2']
    }
    
    def dependenciesDoNotIncludeTheChildrenOfOtherTopLevelTasks() {
        def task1 = task('task1')
        def task2 = task('task2', 'group1', task1)
        def task3 = task('task3', task1)
        def task4 = task('task4', task2)
        def task5 = task('task5', 'group2', task3, task4)

        when:
        model.calculate([task1, task2, task3, task4, task5])

        then:
        TaskDetails t = (model.getTasksForGroup('group2') as List).first()
        t.dependencies as List == ['task2']
    }

    def addsAGroupThatContainsTheTasksWithNoGroup() {
        def task1 = task('task1')
        def task2 = task('task2', 'xgroup', task1)
        def task3 = task('task3')
        def task4 = task('task4', task2)
        def task5 = task('task5', task3, task4)

        when:
        model.calculate([task1, task2, task3, task4, task5])

        then:
        model.groups as List == ['xgroup', 'other']
        def tasks = model.getTasksForGroup('other') as List
        tasks*.task == [task5]
        def t = tasks.first()
        t.task == task5
        t.children*.task as List == [task3, task4]
    }

    def reusesGroupForTasksWithNoGroup() {
        def task1 = task('task1')
        def task2 = task('task2', 'other', task1)
        def task3 = task('task3')

        when:
        model.calculate([task1, task2, task3])

        then:
        model.groups as List == ['other']
        def tasks = model.getTasksForGroup('other') as List
        tasks*.task == [task2,task3]
    }

    def addsAGroupWhenThereAreNoTasksWithAGroup() {
        def task1 = task('task1')
        def task2 = task('task2', task1)
        def task3 = task('task3')

        when:
        model.calculate([task1, task2, task3])

        then:
        model.groups as List == ['']
        def tasks = model.getTasksForGroup('') as List
        tasks*.task == [task2, task3]
    }

    def buildsModelWhenThereAreNoTasks() {
        when:
        model.calculate([])

        then:
        model.groups as List == []
    }

    def ignoresReachableTasksOutsideTheProject() {
        def other1 = task('other1')
        def other2 = task('other2', other1)
        def task1 = task('task1', other2)
        def task2 = task('task2', 'group1', task1)

        when:
        model.calculate([task1, task2])

        then:
        TaskDetails t = (model.getTasksForGroup('group1') as List).first()
        t.children*.task as List == [task1]
        t.dependencies as List == [':other2']
    }

    def task(String name, String group = null, Task... dependencies) {
        Task task = Mock()
        _ * task.toString() >> name
        _ * task.name >> name
        _ * task.path >> ":$name"
        _ * task.group >> group
        _ * task.compareTo(!null) >> { args -> name.compareTo(args[0].name) }
        TaskDependency dep = Mock()
        _ * dep.getDependencies(task) >> {dependencies as Set}
        _ * task.taskDependencies >> dep
        return task
    }
}
