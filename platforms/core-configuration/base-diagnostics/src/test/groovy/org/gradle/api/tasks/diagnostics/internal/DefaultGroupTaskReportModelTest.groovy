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

class DefaultGroupTaskReportModelTest extends AbstractTaskModelSpec {
    final TaskReportModel target = Mock()

    def mergesDefaultGroupIntoOtherGroup() {
        TaskDetails task1 = taskDetails('1')
        TaskDetails task2 = taskDetails('2')
        TaskDetails task3 = taskDetails('3')
        _ * target.groups >> ['a', '', 'other']
        _ * target.getTasksForGroup('a') >> [task1]
        _ * target.getTasksForGroup('') >> [task2]
        _ * target.getTasksForGroup('other') >> [task3]

        when:
        final model = DefaultGroupTaskReportModel.of(target)

        then:
        model.groups as List == ['a', 'other']
        model.getTasksForGroup('a') as List == [task1]
        model.getTasksForGroup('other') as List == [task2, task3]
    }

    def groupNamesAreOrderedCaseInsensitive() {
        TaskDetails task1 = taskDetails('task1')
        TaskDetails task2 = taskDetails('task2')
        TaskDetails task3 = taskDetails('task3')
        TaskDetails task4 = taskDetails('task4')
        TaskDetails task5 = taskDetails('task5')

        _ * target.groups >> (['Abc', 'a', 'A', '', 'Other'] as LinkedHashSet)
        _ * target.getTasksForGroup('a') >> [task1]
        _ * target.getTasksForGroup('A') >> [task2]
        _ * target.getTasksForGroup('Abc') >> [task3]
        _ * target.getTasksForGroup('') >> [task4]
        _ * target.getTasksForGroup('Other') >> [task5]

        when:
        final model = DefaultGroupTaskReportModel.of(target)

        then:
        model.groups as List == ['A', 'a', 'Abc', 'Other']
        model.getTasksForGroup('Other') as List == [task4, task5]
    }

    def taskNamesAreOrderedCaseInsensitiveByNameThenPath() {
        def task1 = taskDetails('task_A')
        def task2 = taskDetails('a:task_A')
        def task3 = taskDetails('a:a:task_A')
        def task4 = taskDetails('B:task_A')
        def task5 = taskDetails('c:task_A')
        def task6 = taskDetails('b:task_a')
        def task7 = taskDetails('a:task_Abc')
        def task8 = taskDetails('task_b')
        _ * target.groups >> ['group']
        _ * target.getTasksForGroup('group') >> ([task6, task3, task7, task4, task5, task1, task8, task2] as LinkedHashSet)

        when:
        final model = DefaultGroupTaskReportModel.of(target)

        then:
        model.getTasksForGroup('group') as List == [task1, task2, task3, task4, task5, task6, task7, task8]
    }

    def renamesDefaultGroupWhenOtherGroupNotPresent() {
        TaskDetails task1 = taskDetails('1')
        TaskDetails task2 = taskDetails('2')
        _ * target.groups >> ['a', '']
        _ * target.getTasksForGroup('a') >> [task1]
        _ * target.getTasksForGroup('') >> [task2]

        when:
        final model = DefaultGroupTaskReportModel.of(target)

        then:
        model.groups as List == ['a', 'other']
        model.getTasksForGroup('a') as List == [task1]
        model.getTasksForGroup('other') as List == [task2]
    }

    def doesNotRenameDefaultGroupWhenItIsTheOnlyGroup() {
        TaskDetails task1 = taskDetails('1')
        _ * target.groups >> ['']
        _ * target.getTasksForGroup('') >> [task1]

        when:
        final model = DefaultGroupTaskReportModel.of(target)

        then:
        model.groups as List == ['']
        model.getTasksForGroup('') as List == [task1]
    }
}
