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
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.initialization.ProjectAccessListener
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class TasksFromProjectDependenciesTest extends AbstractProjectBuilderSpec {

    def dependencies = Mock(DependencySet)
    def context = Mock(TaskDependencyResolveContext)
    def projectAccessListener = Mock(ProjectAccessListener)
    def project1 = TestUtil.create(temporaryFolder).rootProject()
    def project2 = TestUtil.createChildProject(project1, "project2")
    def projectDep1 = Mock(ProjectDependency) { getDependencyProject() >> project1 }
    def projectDep2 = Mock(ProjectDependency) { getDependencyProject() >> project2 }
    def taskContainerDummy = project1.tasks

    def "provides tasks from project dependencies"() {
        def tasks = new TasksFromProjectDependencies("buildNeeded", dependencies, projectAccessListener)

        project1.tasks.create "buildNeeded"
        project2.tasks.create "foo"

        when: tasks.resolveProjectDependencies(context, [projectDep1, projectDep2] as Set)

        then:
        1 * context.add(project1.tasks["buildNeeded"])
        1 * context.getTask()
        0 * context._
    }

    def "notifies the listener about project access"() {
        def tasks = new TasksFromProjectDependencies("buildNeeded", dependencies, projectAccessListener)

        def project1 = Mock(ProjectInternal) { getTasks() >> taskContainerDummy }
        def projectDep1 = Mock(ProjectDependency) { getDependencyProject() >> project1}

        when: tasks.resolveProjectDependencies(context, [projectDep1] as Set)

        then:
        1 * projectAccessListener.beforeResolvingProjectDependency(project1)
    }
}
