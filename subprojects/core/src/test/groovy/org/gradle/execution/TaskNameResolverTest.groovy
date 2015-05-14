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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.model.ModelMap
import org.gradle.model.internal.fixture.ModelRegistryHelper
import spock.lang.Specification

import static org.gradle.model.internal.core.ModelNode.State.*
import static org.gradle.model.internal.core.ModelPath.path

class TaskNameResolverTest extends Specification {
    def tasks = Mock(TaskContainerInternal)
    def registry = new ModelRegistryHelper()
    def project = Mock(ProjectInternal)
    def resolver = new TaskNameResolver()

    def setup() {
        _ * project.getTasks() >> Mock(TaskContainerInternal) {
            _ * findByName(_)
            _ * getTasks() >> tasks
            _ * getNames() >> tasks.getNames()
            0 * _
        }
        _ * project.getModelRegistry() >> registry

        createTasksCollection(registry, "root")
    }

    private final TaskNameResolver resolver = new TaskNameResolver()

    def "eagerly locates task with given name for single project"() {
        when:
        tasks(registry) { it.create("task") }
        def candidates = resolver.selectWithName('task', project, false)

        then:
        registry.state(path("tasks")) == SelfClosed
        registry.state(path("tasks.task")) == Known

        and:
        asTasks(candidates).size() == 1
        registry.state(path("tasks.task")) == GraphClosed
    }

    def "returns null when no task with given name for single project"() {
        expect:
        resolver.selectWithName('task', project, false) == null
        registry.state(path("tasks")) == SelfClosed
    }

    def "eagerly locates tasks with given name for multiple projects"() {
        given:
        def childRegistry = new ModelRegistryHelper()

        def childProject = Mock(ProjectInternal) {
            _ * getModelRegistry() >> childRegistry
            _ * getTasks() >> Mock(TaskContainerInternal) {
                _ * findByName(_)
                0 * _
            }
            _ * getChildProjects() >> [:]
        }

        _ * project.getChildProjects() >> [child: childProject]
        createTasksCollection(childRegistry, "child")

        when:
        tasks(registry) { it.create("task") }
        tasks(childRegistry) { it.create("task") }
        def results = resolver.selectWithName('task', project, true)

        then:
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == SelfClosed
        registry.state(path("tasks.task")) == GraphClosed
        childRegistry.state(path("tasks.task")) == GraphClosed

        and:
        asTasks(results)*.description == ["root", "child"]
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == SelfClosed
        registry.state(path("tasks.task")) == GraphClosed
        childRegistry.state(path("tasks.task")) == GraphClosed
    }

    def "does not select tasks in sub projects when task implies sub projects"() {
        given:
        def childRegistry = new ModelRegistryHelper()
        def childProject = Mock(ProjectInternal) {
            _ * getModelRegistry() >> childRegistry
            _ * getTasks() >> Mock(TaskContainerInternal) {
                _ * findByName(_)
                0 * _
            }
            _ * getChildProjects() >> [:]
        }

        _ * project.getChildProjects() >> [child: childProject]
        createTasksCollection(childRegistry, "child")

        when:
        tasks(registry) { it.create("task") { _ * it.getImpliesSubProjects() >> true } }
        def results = resolver.selectWithName('task', project, true)

        then:
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == Known
        registry.state(path("tasks.task")) == GraphClosed

        and:
        asTasks(results)*.description == ["root"]
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == Known
        registry.state(path("tasks.task")) == GraphClosed
    }

    def "locates tasks in child projects with given name when missing in starting project"() {
        given:
        def childRegistry = new ModelRegistryHelper()
        def childProject = Mock(ProjectInternal) {
            _ * getModelRegistry() >> childRegistry
            _ * getTasks() >> Mock(TaskContainerInternal) {
                _ * findByName(_)
                0 * _
            }
            _ * getChildProjects() >> [:]
        }

        _ * project.getChildProjects() >> [child: childProject]
        createTasksCollection(childRegistry, "child")

        when:
        tasks(childRegistry) { it.create("task") }
        def result = resolver.selectWithName('task', project, true)

        then:
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks.task")) == GraphClosed

        and:
        asTasks(result)*.description == ["child"]
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks.task")) == GraphClosed
    }

    def "lazily locates all tasks for a single project"() {
        when:
        tasks(registry) { it.create("task1"); it.create("task2") }

        then:
        resolver.selectAll(project, false).keySet() == ["task1", "task2"].toSet()
        registry.state(path("tasks.task1")) == Known
        registry.state(path("tasks.task2")) == Known
    }

    def "lazily locates all tasks for multiple projects"() {
        def childRegistry = new ModelRegistryHelper()
        def childProject = Mock(ProjectInternal) {
            _ * getModelRegistry() >> childRegistry
            _ * getTasks() >> Mock(TaskContainerInternal) {
                _ * findByName(_)
                0 * _
            }
            _ * getChildProjects() >> [:]
        }

        _ * project.getChildProjects() >> [child: childProject]
        createTasksCollection(childRegistry, "child")

        tasks(registry) { it.create("name1"); it.create("name2") }
        tasks(childRegistry) { it.create("name1"); it.create("name3") }

        when:
        def candidates = resolver.selectAll(project, true)

        then:
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == SelfClosed

        registry.state(path("tasks.name1")) == Known
        registry.state(path("tasks.name2")) == Known
        childRegistry.state(path("tasks.name1")) == Known
        childRegistry.state(path("tasks.name3")) == Known

        and:
        asTasks(candidates.get('name1'))*.description == ["root", "child"]
        registry.state(path("tasks.name1")) == GraphClosed
        childRegistry.state(path("tasks.name1")) == GraphClosed
        registry.state(path("tasks.name2")) == Known
        childRegistry.state(path("tasks.name3")) == Known

        asTasks(candidates.get('name2'))*.description == ["root"]
        registry.state(path("tasks.name2")) == GraphClosed
        childRegistry.state(path("tasks.name3")) == Known

        asTasks(candidates.get('name3'))*.description == ["child"]
        childRegistry.state(path("tasks.name3")) == GraphClosed
    }

    def "does not visit sub-projects when task implies sub-projects"() {
        def childRegistry = new ModelRegistryHelper()
        def childProject = Mock(ProjectInternal) {
            _ * getModelRegistry() >> childRegistry
            _ * getTasks() >> Mock(TaskContainerInternal) {
                _ * findByName(_)
                0 * _
            }
            _ * getChildProjects() >> [:]
        }

        _ * project.getChildProjects() >> [child: childProject]
        createTasksCollection(childRegistry, "child")

        tasks(registry) { it.create("name1") { it.getImpliesSubProjects() >> true }; it.create("name2") }
        tasks(childRegistry) { it.create("name1"); it.create("name3") }

        when:
        def candidates = resolver.selectAll(project, true)

        then:
        registry.state(path("tasks")) == SelfClosed
        childRegistry.state(path("tasks")) == SelfClosed

        registry.state(path("tasks.name1")) == Known
        registry.state(path("tasks.name2")) == Known
        childRegistry.state(path("tasks.name1")) == Known
        childRegistry.state(path("tasks.name3")) == Known

        and:
        asTasks(candidates.get('name1'))*.description == ["root"]
        registry.state(path("tasks.name1")) == GraphClosed
        childRegistry.state(path("tasks.name1")) == Known
        registry.state(path("tasks.name2")) == Known
        childRegistry.state(path("tasks.name3")) == Known
    }

    def task(String name, String description = "") {
        Stub(TaskInternal) { TaskInternal task ->
            _ * task.getName() >> name
            _ * task.getDescription() >> description
            _ * task.configure(_) >> { task.with(it[0]); task }
        }
    }

    def tasks(ModelRegistryHelper registry, Action<? super ModelMap<TaskInternal>> action) {
        registry.mutateModelMap("tasks", TaskInternal, action)
    }

    Set<Task> asTasks(TaskSelectionResult taskSelectionResult) {
        def result = []
        taskSelectionResult.collectTasks(result)
        return result
    }

    private ModelRegistryHelper createTasksCollection(ModelRegistryHelper registry, String description) {
        registry.modelMap("tasks", TaskInternal) {
            it.registerFactory(TaskInternal) {
                task(it, description)
            }
        }
    }
}
