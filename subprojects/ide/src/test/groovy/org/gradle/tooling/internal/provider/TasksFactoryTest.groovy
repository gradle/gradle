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
import org.gradle.api.internal.AbstractTask
import org.gradle.util.HelperUtil
import spock.lang.Specification

class TasksFactoryTest extends Specification {
    final Project project = Mock()
    final org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3 eclipseProject = Mock()
    final task = HelperUtil.createTask(AbstractTask)

    def "creates task"() {
        task.description = "foo"
        TasksFactory factory = new TasksFactory(true)

        when:
        def createdTask = factory.createDefaultTask(eclipseProject, task);

        then:
        createdTask.name == task.name
        createdTask.description == task.description
        createdTask.path == task.path
    }

    def "does not create tasks"() {
        TasksFactory factory = new TasksFactory(false)

        when:
        factory.allTasks = [:]
        factory.allTasks.put(project, [task] as Set)
        def tasks = factory.create(project, eclipseProject)

        then:
        tasks == []
    }

    def "creates tasks"() {
        TasksFactory factory = new TasksFactory(true)

        when:
        factory.allTasks = [:]
        factory.allTasks.put(project, [task] as Set)
        def tasks = factory.create(project, eclipseProject)

        then:
        tasks.size() == 1
    }
}
