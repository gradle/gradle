/*
 * Copyright 2014 the original author or authors.
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


package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import java.util.function.Supplier

class TasksFromProjectDependenciesTest extends AbstractProjectBuilderSpec {

    def dependencies = { Mock(DependencySet) } as Supplier<DependencySet>
    def context = Mock(TaskDependencyResolveContext)
    def project1State = Mock(ProjectState)
    def project2State = Mock(ProjectState)
    def project1 = Mock(ProjectInternal)
    def project2 = Mock(ProjectInternal)
    def projectDep1 = Mock(ProjectDependency) { getDependencyProject() >> project1 }
    def projectDep2 = Mock(ProjectDependency) { getDependencyProject() >> project2 }
    def tasks1 = Mock(TaskContainerInternal)
    def tasks2 = Mock(TaskContainerInternal)

    def setup() {
        _ * project1.owner >> project1State
        _ * project1.tasks >> tasks1
        _ * project2.owner >> project2State
        _ * project2.tasks >> tasks2
    }

    def "provides tasks from project dependencies"() {
        def tasks = new TasksFromProjectDependencies("buildNeeded", dependencies, TestFiles.taskDependencyFactory())
        def task = Mock(TaskInternal)

        when:
        tasks.resolveProjectDependencies(context, [projectDep1, projectDep2] as Set)

        then:
        1 * project1State.ensureTasksDiscovered()
        1 * tasks1.findByName("buildNeeded") >> task
        1 * project2State.ensureTasksDiscovered()
        1 * tasks2.findByName("buildNeeded") >> null
        1 * context.add(task)
        1 * context.getTask()
        0 * context._
    }
}
