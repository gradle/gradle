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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import spock.lang.Specification

class TaskNameResolverTest extends Specification {
    private final TaskNameResolver resolver = new TaskNameResolver()

    def selectsTaskForSingleProjectWhenThereIsAnExactMatchOnName() {
        ProjectInternal project = Mock()
        TaskContainerInternal tasks = Mock()
        _ * project.tasks >> tasks
        Task task = task('task')
        1 * tasks.getByName('task') >> task

        when:
        def candidates = resolver.select('task', project)

        then:
        1 * tasks.findByName('task') >> task
        asTasks(candidates.get("task")) == [task] as Set
    }

    def selectsImplicitTaskForSingleProjectWhenThereIsAnExactMatchOnName() {
        ProjectInternal project = Mock()
        TaskContainerInternal tasks = Mock()
        TaskContainerInternal implicitTasks = Mock()
        _ * project.tasks >> tasks
        _ * project.implicitTasks >> implicitTasks

        Task task = task('task')
        1 * implicitTasks.getByName('task') >> task

        when:
        def candidates = resolver.select('task', project)

        then:
        1 * tasks.findByName('task') >> null
        1 * implicitTasks.findByName('task') >> task
        asTasks(candidates.get('task')) == [task] as Set
    }

    def selectsAllTasksForSingleProjectWhenThereIsNoExactMatchOnName() {
        ProjectInternal project = Mock()
        TaskContainerInternal tasks = Mock()
        TaskContainerInternal implicitTasks = Mock()
        _ * project.tasks >> tasks
        _ * project.implicitTasks >> implicitTasks

        Task task1 = task('task1')
        Task task2 = task('task2')
        1 * tasks.getByName('task1') >> task1
        1 * implicitTasks.getByName('task2') >> task2

        when:
        def candidates = resolver.select('task', project)

        then:
        1 * tasks.findByName('task') >> null
        1 * implicitTasks.findByName('task') >> null
        1 * tasks.names >> (['task1'] as SortedSet)
        1 * implicitTasks.names >> (['task2', 'task1'] as SortedSet)
        asTasks(candidates.get('task1')) == [task1] as Set
        asTasks(candidates.get('task2')) == [task2] as Set
    }


    def selectsTasksForMultipleProjectsWhenThereIsAnExactMatchOnName() {
        ProjectInternal project = Mock()
        TaskContainerInternal tasks = Mock()
        ProjectInternal childProject = Mock()
        TaskContainerInternal childProjectTasks = Mock()
        _ * project.tasks >> tasks
        _ * project.subprojects >> ([childProject] as Set)
        _ * childProject.tasks >> childProjectTasks

        Task task1 = task('task')
        _ * tasks.getByName('task') >> task1
        Task task2 = task('task')
        _ * childProjectTasks.getByName('task') >> task2

        when:
        def candidates = resolver.selectAll('task', project)

        then:
        1 * tasks.findByName('task') >> task1
        1 * childProjectTasks.findByName('task') >> task2
        asTasks(candidates.get('task')) == [task1, task2] as Set
    }

    def selectsImplicitTaskForMultipleProjectsWhenThereIsAnExactMatchOnName() {
        ProjectInternal project = Mock()
        TaskContainerInternal tasks = Mock()
        TaskContainerInternal implicitTasks = Mock()
        ProjectInternal childProject = Mock()
        TaskContainerInternal childProjectTasks = Mock()
        _ * project.tasks >> tasks
        _ * project.implicitTasks >> implicitTasks
        _ * project.subprojects >> ([childProject] as Set)
        _ * childProject.tasks >> childProjectTasks

        Task task1 = task('task')
        _ * implicitTasks.getByName('task') >> task1
        Task task2 = task('task')
        _ * childProjectTasks.getByName('task') >> task2

        when:
        def candidates = resolver.selectAll('task', project)

        then:
        1 * tasks.findByName('task') >> null
        1 * implicitTasks.findByName('task') >> task1
        1 * childProjectTasks.findByName('task') >> task2
        asTasks(candidates.get('task')) == [task1, task2] as Set
    }

    def selectsAllTasksForMultipleProjectsWhenThereIsNoExactMatchOnName() {
        ProjectInternal project = Mock()
        TaskContainerInternal tasks = Mock()
        TaskContainerInternal implicitTasks = Mock()
        ProjectInternal childProject = Mock()
        TaskContainerInternal childProjectTasks = Mock()
        _ * project.tasks >> tasks
        _ * project.implicitTasks >> implicitTasks
        _ * project.subprojects >> ([childProject] as Set)
        _ * childProject.tasks >> childProjectTasks

        Task task1 = task('name1')
        Task task2 = task('name2')
        Task task3 = task('name1')
        Task task4 = task('name2')
        Task task5 = task('name3')

        _ * childProjectTasks.getByName(task3.name) >> task3
        _ * childProjectTasks.getByName(task4.name) >> task4
        _ * childProjectTasks.getByName(task5.name) >> task5
        _ * tasks.getByName(task1.name) >> task1
        _ * implicitTasks.getByName(task2.name) >> task2

        when:
        def candidates = resolver.selectAll('task', project)

        then:
        1 * tasks.findByName('task') >> null
        1 * implicitTasks.findByName('task') >> null
        1 * childProjectTasks.findByName('task') >> null
        1 * tasks.names >> ([task1.name] as SortedSet)
        1 * implicitTasks.names >> ([task2.name] as SortedSet)
        1 * childProjectTasks.names >> ([task3.name, task4.name, task5.name] as SortedSet)

        asTasks(candidates.get('name1')) == [task1, task3] as Set
        asTasks(candidates.get('name2')) == [task2, task4] as Set
        asTasks(candidates.get('name3')) == [task5] as Set
    }

    def task(String name) {
        Task task = Mock()
        _ * task.name >> name
        return task
    }

    Set<Task> asTasks(Set<TaskSelectionResult> taskSelectionResults) {
        taskSelectionResults.collect { it.getTask() }.toSet()
    }
}
