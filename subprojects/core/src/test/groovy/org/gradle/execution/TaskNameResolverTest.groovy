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
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.TaskContainerInternal
import spock.lang.Specification

class TaskNameResolverTest extends Specification {

    private final TaskNameResolver resolver = new TaskNameResolver()

    def "eagerly locates task with given name for single project"() {
        def task = task('task')
        def projectState = projectState()
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def candidates = resolver.selectWithName('task', projectState, false)

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
        def projectState = projectState()
        def tasks = projectState.getMutableModel().getTasks()
        tasks.names >> (['not-a-task'] as SortedSet)

        expect:
        resolver.selectWithName('task', projectState, false) == null
    }

    def "eagerly locates tasks with given name for multiple projects"() {
        given:
        def task1 = task('task')
        def task2 = task('task')
        def childProjectState = projectState()
        def childTasks = childProjectState.getMutableModel().getTasks()

        def projectState = projectState([childProjectState] as Set)
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def results = resolver.selectWithName('task', projectState, true)

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
        def childProjectState = projectState()
        def childTasks = childProjectState.getMutableModel().getTasks()

        def projectState = projectState([childProjectState] as Set)
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def results = resolver.selectWithName('task', projectState, true)

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
        def childProjectState = projectState()
        def childTasks = childProjectState.getMutableModel().getTasks()

        def projectState = projectState([childProjectState] as Set)
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def results = resolver.selectWithName('task', projectState, true)

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
        def projectState = projectState()
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def result = resolver.selectAll(projectState, false)

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
        def childProjectState = projectState()
        def childTasks = childProjectState.getMutableModel().getTasks()

        def projectState = projectState([childProjectState] as Set)
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def result = resolver.selectAll(projectState, true)

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
        def childProjectState = projectState()
        def childTasks = childProjectState.getMutableModel().getTasks()

        def projectState = projectState([childProjectState] as Set)
        def tasks = projectState.getMutableModel().getTasks()

        when:
        def result = resolver.selectAll(projectState, true)

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

    ProjectState projectState(Set<ProjectState> children = []) {
        def state = Mock(ProjectState) {
            getChildProjects() >> children
        }
        def project = Mock(ProjectInternal) {
            getOwner() >> state
            getTasks() >> Mock(TaskContainerInternal)
        }
        _ * state.getMutableModel() >> project
        state
    }
}
