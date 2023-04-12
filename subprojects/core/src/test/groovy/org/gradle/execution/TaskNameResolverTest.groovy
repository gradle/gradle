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
package org.gradle.execution

import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import spock.lang.Specification

class TaskNameResolverTest extends Specification {
    def tasks = Mock(TaskContainerInternal)
    def project = Mock(ProjectInternal)

    def setup() {
        _ * project.getTasks() >> tasks
    }

    private final TaskNameResolver resolver = new TaskNameResolver()

    def "eagerly locates task with given name for single project"() {
        def task = task('task')

        when:
        def candidates = resolver.selectWithName('task', project, false)

        then:
        1 * tasks.discoverTasks()
        1 * tasks.names >> (['task'] as SortedSet)
        0 * tasks._

        when:
        def matches = asTasks(candidates)

        then:
        matches == [task]

        and:
        1 * tasks.getByName('task') >> task
        0 * tasks._
    }

    def "returns null when no task with given name for single project"() {
        given:
        tasks.names >> (['not-a-task'] as SortedSet)

        expect:
        resolver.selectWithName('task', project, false) == null
    }

    def "eagerly locates tasks with given name for multiple projects"() {
        given:
        def task1 = task('task')
        def task2 = task('task')
        def childTasks = Mock(TaskContainerInternal)
        def childProject = Mock(ProjectInternal) {
            _ * getTasks() >> childTasks
            _ * getChildProjectsUnchecked() >> [:]
        }

        _ * project.childProjectsUnchecked >> [child: childProject]

        when:
        def results = resolver.selectWithName('task', project, true)

        then:
        1 * tasks.discoverTasks()
        1 * childTasks.discoverTasks()
        1 * tasks.names >> (['task'] as SortedSet)
        1 * childTasks.names >> (['task'] as SortedSet)
        1 * tasks.getByName('task') >> task1
        1 * childTasks.getByName('task') >> task2
        0 * tasks._
        0 * childTasks._

        when:
        def matches = asTasks(results)

        then:
        matches == [task1, task2]

        and:
        0 * tasks._
        0 * childTasks._
    }

    def "does not select tasks in sub projects when task implies sub projects"() {
        given:
        def task1 = task('task')
        task1.impliesSubProjects >> true
        def childTasks = Mock(TaskContainerInternal)
        def childProject = Mock(ProjectInternal) {
            _ * getTasks() >> childTasks
            _ * getChildProjectsUnchecked() >> [:]
        }

        _ * project.childProjectsUnchecked >> [child: childProject]

        when:
        def results = resolver.selectWithName('task', project, true)

        then:
        1 * tasks.discoverTasks()
        1 * tasks.names >> (['task'] as SortedSet)
        1 * tasks.getByName('task') >> task1
        0 * tasks._
        0 * childTasks._

        when:
        def matches = asTasks(results)

        then:
        matches == [task1]

        and:
        0 * tasks._
        0 * childTasks._
    }

    def "locates tasks in child projects with given name when missing in starting project"() {
        given:
        def task1 = task('task')
        def childTasks = Mock(TaskContainerInternal)
        def childProject = Mock(ProjectInternal) {
            _ * getTasks() >> childTasks
            _ * getChildProjectsUnchecked() >> [:]
        }
        _ * project.childProjectsUnchecked >> [child: childProject]

        when:
        def results = resolver.selectWithName('task', project, true)

        then:
        1 * tasks.discoverTasks()
        1 * childTasks.discoverTasks()
        1 * tasks.names >> (['not-a-task'] as SortedSet)
        1 * tasks.findByName('task') >> null
        1 * childTasks.names >> (['task'] as SortedSet)
        1 * childTasks.getByName('task') >> task1
        0 * tasks._
        0 * childTasks._

        when:
        def matches = asTasks(results)

        then:
        matches == [task1]

        and:
        0 * tasks._
        0 * childTasks._
    }

    def "lazily locates all tasks for a single project"() {
        given:
        def task1 = task('task1')

        when:
        def result = resolver.selectAll(project, false)

        then:
        result.keySet() == ['task1', 'task2'] as Set

        and:
        1 * tasks.discoverTasks()
        1 * tasks.names >> (['task1', 'task2'] as SortedSet)
        0 * tasks._

        when:
        def matches = asTasks(result['task1'])

        then:
        matches == [task1]

        and:
        1 * tasks.getByName('task1') >> task1
        0 * tasks._
    }

    def "lazily locates all tasks for multiple projects"() {
        given:
        def task1 = task('task1')
        def task2 = task('task1')
        def childTasks = Mock(TaskContainerInternal)
        def childProject = Mock(ProjectInternal) {
            _ * getTasks() >> childTasks
            _ * getChildProjectsUnchecked() >> [:]
        }
        _ * project.childProjectsUnchecked >> [child: childProject]

        when:
        def result = resolver.selectAll(project, true)

        then:
        result.keySet() == ['task1', 'task2', 'task3'] as Set

        and:
        1 * tasks.discoverTasks()
        1 * tasks.names >> (['task1', 'task2'] as SortedSet)
        0 * tasks._
        1 * childTasks.discoverTasks()
        1 * childTasks.names >> (['task1', 'task3'] as SortedSet)
        0 * childTasks._

        when:
        def matches = asTasks(result['task1'])

        then:
        matches == [task1, task2]

        and:
        1 * tasks.names >> (['task1', 'task2'] as SortedSet)
        1 * tasks.getByName('task1') >> task1
        1 * childTasks.names >> (['task1', 'task3'] as SortedSet)
        1 * childTasks.getByName('task1') >> task2
        0 * tasks._
        0 * childTasks._
    }

    def "does not visit sub-projects when task implies sub-projects"() {
        given:
        def task1 = task('task1')
        task1.impliesSubProjects >> true
        def childTasks = Mock(TaskContainerInternal)
        def childProject = Mock(ProjectInternal) {
            _ * getTasks() >> childTasks
            _ * getChildProjectsUnchecked() >> [:]
        }
        _ * project.childProjectsUnchecked >> [child: childProject]

        when:
        def result = resolver.selectAll(project, true)

        then:
        result.keySet() == ['task1', 'task2', 'task3'] as Set

        and:
        1 * tasks.discoverTasks()
        1 * tasks.names >> (['task1', 'task2'] as SortedSet)
        0 * tasks._
        1 * childTasks.discoverTasks()
        1 * childTasks.names >> (['task1', 'task3'] as SortedSet)
        0 * childTasks._

        when:
        def matches = asTasks(result['task1'])

        then:
        matches == [task1]

        and:
        1 * tasks.names >> (['task1', 'task2'] as SortedSet)
        1 * tasks.getByName('task1') >> task1
        0 * tasks._
        0 * childTasks._
    }

    def task(String name, String description = "") {
        Stub(TaskInternal) { TaskInternal task ->
            _ * task.getName() >> name
            _ * task.getDescription() >> description
        }
    }

    private static List<Task> asTasks(TaskSelectionResult taskSelectionResult) {
        def result = []
        taskSelectionResult.collectTasks(result)
        return result
    }
}
