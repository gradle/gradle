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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.Path

class TaskDetailsFactoryTest extends AbstractTaskModelSpec {
    final Project project = Mock()
    final Project subproject = Mock()
    final Task task = Mock()
    TaskDetailsFactory factory

    def setup() {
        project.allprojects >> [project, subproject]
        factory = new TaskDetailsFactory(project)
    }

    def createsDetailsForTaskInMainProject() {
        task.project >> project
        task.path >> ':path'
        project.relativeProjectPath(':path') >> 'task'

        expect:
        def details = factory.create(task)
        details.path == Path.path('task')
    }

    def createsDetailsForTaskInSubProject() {
        task.project >> subproject
        task.path >> ':sub:path'
        project.relativeProjectPath(':sub:path') >> 'sub:task'

        expect:
        def details = factory.create(task)
        details.path == Path.path('sub:task')
    }

    def createsDetailsForTaskInOtherProject() {
        Project other = Mock()
        task.project >> other
        task.path >> ':other:task'

        expect:
        def details = factory.create(task)
        details.path == Path.path(':other:task')
    }

    def providesValuesForOtherProperties() {
        task.project >> project
        task.name >> 'task'
        task.description >> 'description'
        task.path >> ':path'
        project.relativeProjectPath(':path') >> 'task'

        expect:
        def details = factory.create(task)
        details.path.toString() == 'task'
        details.description == 'description'
    }
}
