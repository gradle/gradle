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
    def project = Mock(ProjectInternal) {
        getTasks() >> tasks
    }
    private final TaskNameResolver resolver = new TaskNameResolver()

    def "eagerly locates task with given name for single project"() {
        def task = task('task')

        when:
        def candidates = resolver.selectWithName('task', project, false)

        then:
        1 * tasks.findByName('task') >> task

        when:
        asTasks(candidates) == [task]

        then:
        0 * tasks._
    }

    def "returns null when no task with given name for single project"() {
        when:
        def candidates = resolver.selectWithName('task', project, false)

        then:
        candidates == null
        1 * tasks.findByName('task') >> null
    }

    def "eagerly locates tasks with given name for multiple projects"() {
        def childProject = Mock(ProjectInternal)
        def childProjectTasks = Mock(TaskContainerInternal)
        _ * project.childProjects >> [child: childProject]
        _ * childProject.tasks >> childProjectTasks
        _ * childProject.childProjects >> [:]

        def task1 = task('task')
        def task2 = task('task')

        when:
        def candidates = resolver.selectWithName('task', project, true)

        then:
        1 * tasks.findByName('task') >> task1
        1 * childProjectTasks.findByName('task') >> task2

        when:
        asTasks(candidates) == [task1, task2]

        then:
        0 * tasks._
        0 * childProjectTasks._
    }

    def "does not select tasks in sub projects when task implies sub projects"() {
        def childProject = Mock(ProjectInternal)
        def childProjectTasks = Mock(TaskContainerInternal)
        _ * project.childProjects >> [child: childProject]
        _ * childProject.tasks >> childProjectTasks
        _ * childProject.childProjects >> [:]

        def task1 = task('task')
        _ * task1.impliesSubProjects >> true

        when:
        def candidates = resolver.selectWithName('task', project, true)

        then:
        1 * tasks.findByName('task') >> task1

        when:
        asTasks(candidates) == [task1]

        then:
        0 * tasks._
        0 * childProjectTasks._
    }

    def "locates tasks in child projects with given name when missing in starting project"() {
        def childProject = Mock(ProjectInternal)
        def childProjectTasks = Mock(TaskContainerInternal)
        _ * project.childProjects >> [child: childProject]
        _ * childProject.tasks >> childProjectTasks
        _ * childProject.childProjects >> [:]

        def task1 = task('task')

        when:
        def candidates = resolver.selectWithName('task', project, true)

        then:
        1 * tasks.findByName('task') >> null
        1 * childProjectTasks.findByName('task') >> task1

        when:
        asTasks(candidates) == [task1]

        then:
        0 * tasks._
        0 * childProjectTasks._
    }

    def "lazily locates all tasks for a single project"() {
        def task1 = task('task1')

        when:
        def candidates = resolver.selectAll(project, false)

        then:
        1 * tasks.names >> (['task1', 'task2'] as SortedSet)
        0 * tasks._

        when:
        asTasks(candidates.get('task1')) == [task1]

        then:
        1 * tasks.getByName('task1') >> task1
        0 * tasks._
    }

    def "lazily locates all tasks for multiple projects"() {
        def childProject = Mock(ProjectInternal)
        def childProjectTasks = Mock(TaskContainerInternal)
        _ * project.childProjects >> [child: childProject]
        _ * childProject.tasks >> childProjectTasks
        _ * childProject.childProjects >> [:]

        def task1 = task('name1')
        def task2 = task('name2')

        when:
        def candidates = resolver.selectAll(project, true)

        then:
        1 * tasks.names >> (['name1', 'name2'] as SortedSet)
        1 * childProjectTasks.names >> (['name1', 'name3'] as SortedSet)
        0 * tasks._
        0 * childProjectTasks._

        when:
        asTasks(candidates.get('name1')) == [task1, task2]

        then:
        1 * tasks.findByName('name1') >> task1
        1 * childProjectTasks.findByName('name1') >> task2
        0 * tasks._
        0 * childProjectTasks._
    }

    def "does not visit sub-projects when task implies sub-projects"() {
        def childProject = Mock(ProjectInternal)
        def childProjectTasks = Mock(TaskContainerInternal)
        _ * project.childProjects >> [child: childProject]
        _ * childProject.tasks >> childProjectTasks
        _ * childProject.childProjects >> [:]

        def task1 = task('name1')
        _ * task1.impliesSubProjects >> true

        when:
        def candidates = resolver.selectAll(project, true)

        then:
        1 * tasks.names >> (['name1', 'name2'] as SortedSet)
        1 * childProjectTasks.names >> (['name1', 'name3'] as SortedSet)
        0 * tasks._
        0 * childProjectTasks._

        when:
        asTasks(candidates.get('name1')) == [task1]

        then:
        1 * tasks.findByName('name1') >> task1
        0 * tasks._
        0 * childProjectTasks._
    }

    def task(String name) {
        TaskInternal task = Mock()
        _ * task.name >> name
        return task
    }

    Set<Task> asTasks(TaskSelectionResult taskSelectionResult) {
        def result = []
        taskSelectionResult.collectTasks(result)
        return result
    }
}
