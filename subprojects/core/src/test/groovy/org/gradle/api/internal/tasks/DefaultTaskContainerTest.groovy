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

package org.gradle.api.internal.tasks

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.tasks.TaskDependency
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.util.GUtil
import spock.lang.Specification

import static java.util.Collections.singletonMap

public class DefaultTaskContainerTest extends Specification {

    private taskFactory = Mock(ITaskFactory)
    def modelRegistry = new DefaultModelRegistry(null, null)
    private project = Mock(ProjectInternal, name: "<project>") {
        getModelRegistry() >> modelRegistry
    }
    private taskCount = 1;
    private accessListener = Mock(ProjectAccessListener)
    private container = new DefaultTaskContainerFactory(modelRegistry, DirectInstantiator.INSTANCE, taskFactory, project, accessListener).create()

    void "creates by Map"() {
        def options = singletonMap("option", "value")
        def task = task("task")
        taskFactory.createTask(options) >> task

        when:
        def added = container.create(options)

        then:
        added == task
        container.getByName("task") == task
    }

    void "creates by name"() {
        given:
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")
        taskFactory.createTask(options) >> task

        expect:
        container.create("task") == task
    }

    void "creates by name and type"() {
        given:
        def options = GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, Task.class)
        def task = task("task")
        taskFactory.createTask(options) >> task

        expect:
        container.create("task", Task.class) == task
    }

    void "creates by name and closure"() {
        given:
        final Closure action = {}
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")

        taskFactory.createTask(options) >> task

        when:
        def added = container.create("task", action)

        then:
        added == task
        1 * task.configure(action) >> task
    }

    void "creates by name and action"() {
        given:
        def action = Mock(Action)
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")

        taskFactory.createTask(options) >> task

        when:
        def added = container.create("task", action)

        then:
        added == task
        1 * action.execute(task)
    }

    void "replaces task by name"() {
        given:
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")
        taskFactory.createTask(options) >> task

        when:
        def replaced = container.replace("task")

        then:
        replaced == task
        container.getByName("task") == task
    }

    void "replaces by name and type"() {
        given:
        def options = GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, Task.class)
        def task = task("task")
        taskFactory.createTask(options) >> task

        expect:
        container.replace("task", Task.class) == task
    }

    void "does not fire rule when adding task"() {
        def rule = Mock(Rule)
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")

        container.addRule(rule)
        taskFactory.createTask(options) >> task

        when:
        container.create("task")

        then:
        0 * rule._
    }

    void "prevents duplicate tasks"() {
        given:
        def task = addTask("task")
        taskFactory.createTask(singletonMap(Task.TASK_NAME, "task")) >> { this.task("task") }

        when:
        container.create("task")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot add Mock for type 'TaskInternal' named '[task1]' as a task with that name already exists."
        container.getByName("task") == task
    }

    void "replaces duplicate task"() {
        given:
        addTask("task")
        def newTask = task("task")
        taskFactory.createTask(singletonMap(Task.TASK_NAME, "task")) >> newTask

        when:
        container.replace("task")

        then:
        container.getByName("task") == newTask
    }

    void "fails if unknown task is requested"() {
        when:
        container.getByName("unknown")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'unknown' not found in Mock for type 'ProjectInternal' named '<project>'."
    }

    void "finds tasks"() {
        when:
        def task = addTask("task")

        then:
        container.findByPath("unknown") == null
        container.findByPath("task") == task
        container.findByName("task") == task
    }

    void "finds task by relative path"() {
        when:
        Task task = task("task")
        expectTaskLookupInOtherProject("sub", "task", task)

        then:
        container.findByPath("sub:task") == task
    }

    void "finds tasks by absolute path"() {
        when:
        Task task = task("task")
        expectTaskLookupInOtherProject(":", "task", task)

        then:
        container.findByPath(":task") == task
    }

    void "does not find tasks from unknown projects"() {
        when:
        project.findProject(":unknown") >> null

        then:
        container.findByPath(":unknown:task") == null
    }

    void "searching by path ensures the other project is evaluated"() {
        given:
        def otherProject = expectTaskLookupInOtherProject(":other", "task", null)

        when:
        container.findByPath(":other:task")

        then:
        1 * accessListener.beforeRequestingTaskByPath(otherProject)
    }

    void "does not find unknown tasks by path"() {
        when:
        expectTaskLookupInOtherProject(":other", "task", null)

        then:
        container.findByPath(":other:task") >> null
    }

    void "gets task by path"() {
        when:
        Task task = addTask("task")
        expectTaskLookupInOtherProject(":a:b:c", "task", task)

        then:
        container.getByPath(":a:b:c:task") == task
    }

    void "get by path fails for unknown task"() {
        when:
        container.getByPath("unknown")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with path 'unknown' not found in Mock for type 'ProjectInternal' named '<project>'."
    }

    void "resolve locates by name"() {
        when:
        Task task = addTask("1")

        then:
        container.resolveTask("1") == task
    }

    void "resolve locates by path"() {
        when:
        Task task = addTask("task")
        expectTaskLookupInOtherProject(":", "task", task)

        then:
        container.resolveTask(":task") == task
    }

    void "actualizes task graph"() {
        given:
        def aTask = addTask("a")
        def bTask = addTask("b")
        aTask.dependsOn(bTask)

        addPlaceholderTask("c")
        def cTask = this.task("c", DefaultTask)
        1 * taskFactory.create("c", DefaultTask) >> { cTask }

        assert container.size() == 2

        when:
        container.realize()

        then:
        0 * aTask.getTaskDependencies()
        0 * bTask.getTaskDependencies()
        0 * cTask.getTaskDependencies()
        container.size() == 3
    }

    void "invokes rule at most once when locating a task"() {
        def rule = Mock(Rule)

        given:
        container.addRule(rule)

        when:
        def result = container.findByName("task")

        then:
        result == null

        and:
        1 * rule.apply("task")
        0 * rule._
    }

    void "can add task via placeholder action"() {
        when:
        addPlaceholderTask("task")
        1 * taskFactory.create("task", DefaultTask) >> { task(it[0], it[1]) }

        then:
        container.getByName("task") != null
    }

    void "placeholder is ignored when task already exists"() {
        given:
        Task task = addTask("task")
        def placeholderAction = addPlaceholderTask("task")

        when:
        container.getByName("task") == task

        then:
        0 * placeholderAction.execute(_)
    }

    void "placeholder is ignored when task later defined"() {
        given:
        def placeholderAction = addPlaceholderTask("task")
        Task task = addTask("task")

        when:
        container.getByName("task") == task

        then:
        0 * placeholderAction.execute(_)
    }

    void "getNames contains task and placeholder action names"() {
        when:
        addTask("task1")
        def placeholderAction = addPlaceholderTask("task2")
        0 * placeholderAction.execute(_)
        then:
        container.names == ['task1', 'task2'] as SortedSet
    }

    void "maybeCreate creates new task"() {
        given:
        def options = singletonMap(Task.TASK_NAME, "task")
        def task = task("task")

        taskFactory.createTask(options) >> task

        when:
        def added = container.maybeCreate("task")

        then:
        added == task
    }

    void "maybeCreate returns existing task"() {
        when:
        def task = addTask("task")

        then:
        container.maybeCreate("task") == task
    }

    void "maybeCreate creates new task with type"() {
        given:
        def options = [name: "task", type: CustomTask]
        def task = task("task", CustomTask)

        taskFactory.createTask(options) >> task

        when:
        def added = container.maybeCreate("task", CustomTask)

        then:
        added == task
    }

    void "maybeCreate returns existing task with type"() {
        when:
        def task = addTask("task", CustomTask)

        then:
        container.maybeCreate("task", CustomTask) == task
    }

    private ProjectInternal expectTaskLookupInOtherProject(final String projectPath, final String taskName, def task) {
        def otherProject = Mock(ProjectInternal)
        def otherTaskContainer = Mock(TaskContainerInternal)

        project.findProject(projectPath) >> otherProject
        otherProject.getTasks() >> otherTaskContainer

        otherTaskContainer.findByName(taskName) >> task

        otherProject
    }

    private TaskInternal task(final String name) {
        task(name, TaskInternal)
    }

    private <U extends TaskInternal> U task(final String name, Class<U> type) {
        Mock(type, name: "[task" + taskCount++ + "]") {
            getName() >> name
            getTaskDependency() >> Mock(TaskDependency)
        }
    }

    private Action addPlaceholderTask(String placeholderName) {
        def action = Mock(Action)
        container.addPlaceholderAction(placeholderName, DefaultTask, action)
        action
    }

    private Task addTask(String name) {
        def task = task(name)
        def options = singletonMap(Task.TASK_NAME, name)
        taskFactory.createTask(options) >> task
        container.create(name)
        return task;
    }

    private <U extends Task> U addTask(String name, Class<U> type) {
        def task = task(name, type)
        def options = [name: name, type: type]
        taskFactory.createTask(options) >> task
        container.create(name, type)
        return task;
    }

    interface CustomTask extends TaskInternal {}
}
