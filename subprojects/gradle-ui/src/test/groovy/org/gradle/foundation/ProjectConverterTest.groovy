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
package org.gradle.foundation

import spock.lang.Specification
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.Task

class ProjectConverterTest extends Specification {
    private final ProjectConverter converter = new ProjectConverter()

    def convertsProjectHierarchy() {
        Project child1 = project('child1')
        Project child2 = project('child2')
        Project root = project('root', child1, child2)
        _ * root.description >> 'description'
        _ * child1.description >> 'child1 description'

        expect:
        def views = converter.convertProjects(root)

        views.size() == 1
        def rootView = views[0]
        rootView.name == 'root'
        rootView.description == 'description'
        rootView.subProjects.size() == 2

        def child1View = rootView.subProjects[0]
        child1View.name == 'child1'
        child1View.description == 'child1 description'
        child1View.parentProject == rootView
        child1View.subProjects.isEmpty()

        def child2View = rootView.subProjects[1]
        child2View.name == 'child2'
        child2View.description == ''
        child2View.parentProject == rootView
        child2View.subProjects.isEmpty()
    }

    def convertsTasks() {
        Task task1 = task('t1')
        _ * task1.description >> 't1 description'
        Task task2 = task('t2')
        Project root = project('root', task1, task2)

        expect:
        def views = converter.convertProjects(root)
        def rootView = views[0]
        rootView.tasks.size() == 2

        def task1View = rootView.tasks[0]
        task1View.name == 't1'
        task1View.description == 't1 description'
        task1View.project == rootView

        def task2View = rootView.tasks[1]
        task2View.name == 't2'
        task2View.description == ''
        task2View.project == rootView
    }

    def task(String name) {
        Task task = Mock()
        _ * task.name >> name
        return task
    }

    def project(String name) {
        project(name, [], [])
    }

    def project(String name, Project... subprojects) {
        project(name, [], subprojects as List)
    }

    def project(String name, Task... tasks) {
        project(name, tasks as List, [])
    }

    def project(String name, Collection<Task> tasks, Collection<Project> subprojects) {
        Project project = Mock()
        TaskContainer taskContainer = Mock()
        _ * project.name >> name
        _ * project.tasks >> taskContainer
        _ * project.defaultTasks >> []
        _ * taskContainer.iterator() >> tasks.iterator()
        _ * project.dependsOnProjects >> []
        _ * project.childProjects >> subprojects.inject([:]) { v, p -> v[p.name] = p; v }
        return project
    }
}
