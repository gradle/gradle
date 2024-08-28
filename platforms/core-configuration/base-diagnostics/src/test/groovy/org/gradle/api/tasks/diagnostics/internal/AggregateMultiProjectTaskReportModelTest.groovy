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

class AggregateMultiProjectTaskReportModelTest extends AbstractTaskModelSpec {

    def "merges the groups from each project"() {
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['p1', 'common'] as LinkedHashSet)
        _ * project2.groups >> (['p2', 'common'] as LinkedHashSet)
        _ * _.getTasksForGroup(_) >> ([taskDetails('task')] as Set)

        def model = model(false, true, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == ['p1', 'common', 'p2'] as Set
    }

    def "merges the groups with a given name from each project into a single group"() {
        TaskDetails task1 = taskDetails('task1')
        TaskDetails task2 = taskDetails('task2')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1] as Set)
        _ * project2.groups >> (['group'] as LinkedHashSet)
        _ * project2.getTasksForGroup('group') >> ([task2] as Set)

        def model = model(false, true, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group') == [task1, task2] as Set
    }

    def "merges the tasks with a given name from each project into a single task"() {
        TaskDetails task1 = taskDetails(':task')
        TaskDetails task2 = taskDetails(':other')
        TaskDetails task3 = taskDetails(':sub:task')
        _ * task1.findTask(_) >> Mock(Task)
        _ * task2.findTask(_) >> Mock(Task)
        _ * task3.findTask(_) >> Mock(Task)

        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1, task2] as Set)
        _ * project2.groups >> (['group'] as LinkedHashSet)
        _ * project2.getTasksForGroup('group') >> ([task3] as Set)

        def model = model(true, true, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group')*.path*.path as Set == ['task', 'other'] as Set
    }

    def "shows tasks from specific group(s)"() {
        TaskDetails task1 = taskDetails(':task')
        TaskDetails task2 = taskDetails(':other')
        TaskDetails task3 = taskDetails(':sub:task')
        _ * task1.findTask(_) >> Mock(Task)
        _ * task2.findTask(_) >> Mock(Task)
        _ * task3.findTask(_) >> Mock(Task)

        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group1'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group1') >> ([task1, task2] as Set)
        _ * project2.groups >> (['group2'] as LinkedHashSet)
        _ * project2.getTasksForGroup('group2') >> ([task3] as Set)

        def model = new AggregateMultiProjectTaskReportModel(true, true, groups)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == groups as Set

        where:
        groups               | _
        ['group1']           | _
        ['group2']           | _
        ['group1', 'group2'] | _
    }

    def "handles group which is not present in each project"() {
        TaskDetails task1 = taskDetails('task1')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1] as Set)
        _ * project2.groups >> ([] as LinkedHashSet)
        0 * project2.getTasksForGroup(_)

        def model = model(false, true, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group') == [task1] as Set
    }

    def "group names are case insensitive"() {
        TaskDetails task1 = taskDetails('task1')
        TaskDetails task2 = taskDetails('task2')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['A'] as LinkedHashSet)
        _ * project1.getTasksForGroup('A') >> ([task1] as Set)
        _ * project2.groups >> (['a'] as LinkedHashSet)
        _ * project2.getTasksForGroup('a') >> ([task2] as Set)

        def model = model(false, true, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == ['A'] as Set
        model.getTasksForGroup('A') == [task1, task2] as Set
    }

    def model(boolean mergeTasksWithSameName, boolean detail, List<String> groups) {
        return new AggregateMultiProjectTaskReportModel(mergeTasksWithSameName, detail, groups)
    }
}
