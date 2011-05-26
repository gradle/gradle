/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import spock.lang.Specification

class TasksFactoryTest extends Specification {
    final Project project = Mock()
    final org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3 eclipseProject = Mock()
    final TaskContainer tasks = Mock()
    final TasksFactory factory = new TasksFactory()

    def "builds the tasks for a project"() {
        def taskA = task('a')
        def taskB = task('b')

        when:
        def result = factory.create(project, eclipseProject, new EclipsePluginApplierResult())

        then:
        result.size() == 2
        result[0].path == ':a'
        result[0].name == 'a'
        result[0].description == 'task a'
        result[0].project == eclipseProject
        result[1].name == 'b'
        1 * project.tasks >> tasks
        tasks.iterator() >> [taskA, taskB].iterator()
    }

    def "skips applied tasks"() {
        def taskA = task('a')
        def taskB = task('b')

        1 * project.tasks >> tasks
        tasks.iterator() >> [taskA, taskB].iterator()

        def applierResult = new EclipsePluginApplierResult()
        applierResult.rememberTasks(":", ['b'])

        when:
        def result = factory.create(project, eclipseProject, applierResult)

        then:
        result.size() == 1
        result[0].path == ':a'
    }

    def task(String name) {
        Task task = Mock()
        _ * task.path >> ":$name"
        _ * task.name >> name
        _ * task.description >> "task $name"
        return task
    }
}
