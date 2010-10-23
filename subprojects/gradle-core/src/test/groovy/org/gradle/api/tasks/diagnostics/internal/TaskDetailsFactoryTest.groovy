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
import org.gradle.api.Project
import org.gradle.util.Path

class TaskDetailsFactoryTest extends TaskModelSpecification {
    final Project project = Mock()
    final Project subproject = Mock()
    final Task task = Mock()
    TaskDetailsFactory factory

    def setup() {
        _ * project.allprojects >> ([project, subproject] as Set)
        factory = new TaskDetailsFactory(project)
    }
    
    def createsDetailsForTaskInMainProject() {
        _ * task.project >> project
        _ * task.path >> ':path'
        _ * project.relativeProjectPath(':path') >> 'task'

        expect:
        def details = factory.create(task)
        details.path == Path.path('task')
    }

    def createsDetailsForTaskInSubProject() {
        _ * task.project >> subproject
        _ * task.path >> ':sub:path'
        _ * project.relativeProjectPath(':sub:path') >> 'sub:task'

        expect:
        def details = factory.create(task)
        details.path == Path.path('sub:task')
    }

    def createsDetailsForTaskInOtherProject() {
        Project other = Mock()
        _ * task.project >> other
        _ * task.path >> ':other:task'

        expect:
        def details = factory.create(task)
        details.path == Path.path(':other:task')
    }

    def providesValuesForOtherProperties() {
        _ * task.project >> project
        _ * task.name >> 'task'
        _ * task.description >> 'description'

        expect:
        def details = factory.create(task)
        details.description == 'description'
        details.dependencies.isEmpty()
        details.children.isEmpty()
    }
}
