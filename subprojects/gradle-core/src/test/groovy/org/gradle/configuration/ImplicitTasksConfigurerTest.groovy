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
package org.gradle.configuration

import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.diagnostics.PropertyReportTask
import org.gradle.api.tasks.diagnostics.TaskReportTask
import spock.lang.Specification

class ImplicitTasksConfigurerTest extends Specification {
    private final ImplicitTasksConfigurer configurer = new ImplicitTasksConfigurer()
    private final ProjectInternal project = Mock()
    private final TaskContainerInternal tasks = Mock()

    def addsImplicitTasksToProject() {
        Task task = Mock()

        when:
        configurer.execute(project)

        then:
        _ * project.implicitTasks >> tasks
        1 * tasks.add('help') >> task
        1 * tasks.add('projects') >> task
        1 * tasks.add('tasks', TaskReportTask) >> task
        1 * tasks.add('dependencies', DependencyReportTask) >> task
        1 * tasks.add('properties', PropertyReportTask) >> task
    }
}
