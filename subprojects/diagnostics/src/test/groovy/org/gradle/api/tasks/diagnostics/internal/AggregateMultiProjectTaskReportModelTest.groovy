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

class AggregateMultiProjectTaskReportModelTest extends AbstractTaskModelSpec {
    final AggregateMultiProjectTaskReportModel model = new AggregateMultiProjectTaskReportModel(false, true)

    def mergesTheGroupsFromEachProject() {
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['p1', 'common'] as LinkedHashSet)
        _ * project2.groups >> (['p2', 'common'] as LinkedHashSet)
        _ * _.getTasksForGroup(_) >> ([taskDetails('task')] as Set)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == ['p1', 'common', 'p2'] as Set
    }

    def mergesTheGroupsWithAGivenNameFromEachProjectIntoASingleGroup() {
        TaskDetails task1 = taskDetails('task1')
        TaskDetails task2 = taskDetails('task2')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1] as Set)
        _ * project2.groups >> (['group'] as LinkedHashSet)
        _ * project2.getTasksForGroup('group') >> ([task2] as Set)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group') == [task1, task2] as Set
    }

    def mergesTheTasksWithAGivenNameFromEachProjectIntoASingleTask() {
        TaskDetails task1 = taskDetails(':task')
        TaskDetails task2 = taskDetails(':other')
        TaskDetails task3 = taskDetails(':sub:task')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1, task2] as Set)
        _ * project2.groups >> (['group'] as LinkedHashSet)
        _ * project2.getTasksForGroup('group') >> ([task3] as Set)

        def model = new AggregateMultiProjectTaskReportModel(true, true)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group')*.path*.path as Set == ['task', 'other'] as Set
    }

    def handlesGroupWhichIsNotPresentInEachProject() {
        TaskDetails task1 = taskDetails('task1')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1] as Set)
        _ * project2.groups >> ([] as LinkedHashSet)
        0 * project2.getTasksForGroup(_)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group') == [task1] as Set
    }

    def groupNamesAreCaseInsensitive() {
        TaskDetails task1 = taskDetails('task1')
        TaskDetails task2 = taskDetails('task2')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['A'] as LinkedHashSet)
        _ * project1.getTasksForGroup('A') >> ([task1] as Set)
        _ * project2.groups >> (['a'] as LinkedHashSet)
        _ * project2.getTasksForGroup('a') >> ([task2] as Set)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == ['A'] as Set
        model.getTasksForGroup('A') == [task1, task2] as Set
    }
}
