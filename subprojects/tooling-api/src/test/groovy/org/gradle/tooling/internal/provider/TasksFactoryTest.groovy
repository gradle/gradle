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

import spock.lang.Specification
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.Task

class TasksFactoryTest extends Specification {
    final Project project = Mock()
    final TaskContainer tasks = Mock()
    final TasksFactory factory = new TasksFactory()

    def "builds the tasks for a project"() {
        def taskA = task('a')
        def taskB = task('b')

        when:
        def result = factory.create(project)

        then:
        result.size() == 2
        result[0].path == ':a'
        result[0].name == 'a'
        result[0].description == 'task a'
        result[1].name == 'b'
        1 * project.tasks >> tasks
        tasks.iterator() >> [taskA, taskB].iterator()
    }

    def task(String name) {
        Task task = Mock()
        _ * task.path >> ":$name"
        _ * task.name >> name
        _ * task.description >> "task $name"
        return task
    }
}
