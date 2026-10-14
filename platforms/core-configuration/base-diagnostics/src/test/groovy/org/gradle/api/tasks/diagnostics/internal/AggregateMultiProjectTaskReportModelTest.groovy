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

class AggregateMultiProjectTaskReportModelTest extends AbstractTaskModelSpec {

    def "merges the groups from each project"() {
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['p1', 'common'] as LinkedHashSet)
        _ * project2.groups >> (['p2', 'common'] as LinkedHashSet)
        _ * _.getTasksForGroup(_) >> ([taskDetails('task')] as Set)

        def model = model(false, true, null, [])

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

        def model = model(false, true, null, [])

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

        def model = model(true, true, null, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.getTasksForGroup('group')*.path*.asString() as Set == ['task', 'other'] as Set
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

        def model = new AggregateMultiProjectTaskReportModel(true, true, group, groups)

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == ([group] + groups - null) as Set

        where:
        group    | groups
        'group1' | []
        'group2' | []
        null     | ['group1']
        null     | ['group1', 'group2']
        'group1' | ['group2']
    }

    def "handles group which is not present in each project"() {
        TaskDetails task1 = taskDetails('task1')
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([task1] as Set)
        _ * project2.groups >> ([] as LinkedHashSet)
        0 * project2.getTasksForGroup(_)

        def model = model(false, true, null, [])

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

        def model = model(false, true, null, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        model.groups == ['A'] as Set
        model.getTasksForGroup('A') == [task1, task2] as Set
    }

    def "merged entry keeps build tree path only when a single task has that name"() {
        def single = TaskDetails.of(Path.path(":a:single"), "TYPE_NAME", null, null, Path.path(":a:single"))
        def shared1 = TaskDetails.of(Path.path(":a:shared"), "TYPE_NAME", "first", null, Path.path(":a:shared"))
        def shared2 = TaskDetails.of(Path.path(":b:shared"), "TYPE_NAME", "second", null, Path.path(":b:shared"))
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([single, shared1] as LinkedHashSet)
        _ * project2.groups >> (['group'] as LinkedHashSet)
        _ * project2.getTasksForGroup('group') >> ([shared2] as LinkedHashSet)

        def model = model(true, false, null, [])

        when:
        model.add(project1)
        model.add(project2)
        model.build()

        then:
        def tasks = model.getTasksForGroup('group') as List
        tasks*.path*.path == ['shared', 'single']
        tasks*.buildTreePath == [null, Path.path(":a:single")]
        tasks[0].description == 'first'
    }

    def "merged entry drops build tree path when a same-named task is hidden or in another group"() {
        def visible = TaskDetails.of(Path.path(":a:shared"), "TYPE_NAME", null, null, Path.path(":a:shared"))
        def hidden = TaskDetails.of(Path.path(":b:shared"), "TYPE_NAME", null, null, Path.path(":b:shared"))
        def otherGroup = TaskDetails.of(Path.path(":c:shared"), "TYPE_NAME", null, null, Path.path(":c:shared"))
        TaskReportModel project1 = Mock()
        TaskReportModel project2 = Mock()
        TaskReportModel project3 = Mock()
        _ * project1.groups >> (['group'] as LinkedHashSet)
        _ * project1.getTasksForGroup('group') >> ([visible] as LinkedHashSet)
        _ * project2.groups >> ([''] as LinkedHashSet)
        _ * project2.getTasksForGroup('') >> ([hidden] as LinkedHashSet)
        _ * project3.groups >> (['other'] as LinkedHashSet)
        _ * project3.getTasksForGroup('other') >> ([otherGroup] as LinkedHashSet)

        def model = model(true, false, 'group', [])

        when:
        model.add(project1)
        model.add(project2)
        model.add(project3)
        model.build()

        then:
        model.groups == ['group'] as Set
        (model.getTasksForGroup('group') as List)*.buildTreePath == [null]
    }

    def model(boolean mergeTasksWithSameName, boolean detail, String group, List<String> groups) {
        return new AggregateMultiProjectTaskReportModel(mergeTasksWithSameName, detail, group, groups)
    }
}
