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
import org.gradle.model.internal.registry.ModelRegistry
import spock.lang.Specification

import static java.util.Collections.singletonMap

class DefaultTaskContainerTest extends Specification {

    private taskFactory = Mock(ITaskFactory)
    def modelRegistry = Mock(ModelRegistry)
    private project = Mock(ProjectInternal, name: "<project>")
    private taskCount = 1;
    private accessListener = Mock(ProjectAccessListener)
    private container = new DefaultTaskContainerFactory(modelRegistry, DirectInstantiator.INSTANCE, taskFactory, project, accessListener, new TaskStatistics()).create()

    void 'cannot create task with no name'() {
        when:
        container.create([:])

        then:
        InvalidUserDataException e = thrown()
        e.message == "The task name must be provided."
    }

    void 'can create task with dependencies'() {
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        def added = container.create([name: 'task', dependsOn: "/path1"])

        then:
        added == task
        1 * task.dependsOn("/path1")
    }

    void 'create fails with unknown arguments'() {
        when:
        container.create([name: 'task', dependson: 'anotherTask'])

        then:
        InvalidUserDataException exception = thrown()
        exception.message == "Could not create task 'task': Unknown argument(s) in task definition: [dependson]"

        when:
        container.create([name: 'task', Type: NotATask])

        then:
        exception = thrown()
        exception.message == "Could not create task 'task': Unknown argument(s) in task definition: [Type]"
    }

    static class NotATask {
    }

    void 'can create task with Action'() {
        Action<Task> action = Mock()
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        Task added = container.create([name: 'task', action: action])

        then:
        added == task
        1 * task.doFirst(action)
    }

    void 'can create task with Action closure'() {
        Closure action = Mock()
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        Task added = container.create([name: 'task', action: action])

        then:
        added == task
        1 * task.doFirst(action)
    }

    void 'can create task with description'() {
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        Task added = container.create([name: 'task', description: "some task"])

        then:
        added == task
        1 * task.setDescription("some task")
    }

    void 'can create task with group'() {
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        Task added = container.create([name: 'task', group: "some group"])

        then:
        added == task
        1 * task.setGroup("some group")
    }

    void "creates by Map"() {
        def options = singletonMap("name", "task")
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        def added = container.create(options)

        then:
        added == task
        container.getByName("task") == task
    }

    void "creates by name"() {
        given:
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        expect:
        container.create("task") == task
        container.names.contains("task")
    }

    void "creates by name and type"() {
        given:
        def task = task("task", CustomTask)
        taskFactory.create("task", CustomTask) >> task

        expect:
        container.create("task", CustomTask.class) == task
    }

    void "creates by name and closure"() {
        given:
        final Closure action = {}
        def task = task("task")

        taskFactory.create("task", DefaultTask) >> task

        when:
        def added = container.create("task", action)

        then:
        added == task
        1 * task.configure(action) >> task
    }

    void "creates by name and action"() {
        given:
        def action = Mock(Action)
        def task = task("task")

        taskFactory.create("task", DefaultTask) >> task

        when:
        def added = container.create("task", action)

        then:
        added == task
        1 * action.execute(task)
    }

    void "replaces task by name"() {
        given:
        def task = task("task")
        taskFactory.create("task", DefaultTask) >> task

        when:
        def replaced = container.replace("task")

        then:
        replaced == task
        container.getByName("task") == task
    }

    void "replaces by name and type"() {
        given:
        def task = task("task", CustomTask)
        taskFactory.create("task", CustomTask) >> task

        expect:
        container.replace("task", CustomTask.class) == task
    }

    void "does not fire rule when adding task"() {
        def rule = Mock(Rule)
        def task = task("task")

        container.addRule(rule)
        taskFactory.create("task", DefaultTask) >> task

        when:
        container.create("task")

        then:
        0 * rule._
    }

    void "prevents duplicate tasks"() {
        given:
        def task = addTask("task")
        1 * taskFactory.create("task", DefaultTask) >> { this.task("task") }

        when:
        container.create("task")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot add task 'task' as a task with that name already exists."
        container.getByName("task") == task
    }

    void "replaces duplicate task"() {
        given:
        addTask("task")
        def newTask = task("task")
        taskFactory.create("task", DefaultTask) >> newTask

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

    void "realizes task graph"() {
        given:
        def aTask = addTask("a")
        def bTask = addTask("b")
        aTask.dependsOn(bTask)

        addPlaceholderTask("c")
        def cTask = this.task("c", DefaultTask)

        when:
        container.realize()

        then:
        1 * taskFactory.create("c", DefaultTask) >> { cTask }
        0 * aTask.getTaskDependencies()
        0 * bTask.getTaskDependencies()
        0 * cTask.getTaskDependencies()
        container.getByName("c") == cTask
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

    void "can define task to create and configure later given name and type"() {
        def action = Mock(Action)

        when:
        def provider = container.createLater("task", DefaultTask, action)

        then:
        0 * _

        and:
        container.names.contains("task")
        container.size() == 1
        !container.empty
        !provider.present
    }

    void "can define task to create and configure later given name"() {
        def action = Mock(Action)
        def task = task("task")

        when:
        def provider = container.createLater("task", action)

        then:
        0 * _

        and:
        container.names.contains("task")
        container.size() == 1

        when:
        def result = provider.get()

        then:
        1 * taskFactory.create("task", DefaultTask) >> task
        1 * action.execute(_)
        result == task
    }

    void "can define task to create later given name and type"() {
        when:
        def provider = container.createLater("task", DefaultTask)

        then:
        0 * _

        and:
        container.names.contains("task")
        container.size() == 1
        !container.empty
        !provider.present
    }

    void "can define task to create later given name"() {
        def task = task("task")

        when:
        def provider = container.createLater("task")

        then:
        0 * _

        and:
        container.names.contains("task")
        container.size() == 1

        when:
        def result = provider.get()

        then:
        1 * taskFactory.create("task", DefaultTask) >> task
        result == task
    }

    void "define task fails when task with given name already defined"() {
        given:
        _ * taskFactory.create("task1", DefaultTask, _) >> task("task1")
        _ * taskFactory.create("task2", DefaultTask, _) >> task("task2")

        container.create("task1")
        container.createLater("task2", {})

        when:
        container.createLater("task1", {})

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Cannot add task 'task1' as a task with that name already exists."

        when:
        container.createLater("task2", {})

        then:
        def e2 = thrown(InvalidUserDataException)
        e2.message == "Cannot add task 'task2' as a task with that name already exists."

        when:
        container.create("task2")

        then:
        def e3 = thrown(InvalidUserDataException)
        e3.message == "Cannot add task 'task2' as a task with that name already exists."
    }

    void "defined task can be created and configured explicitly by using the returned provider"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        def provider = container.createLater("task", DefaultTask, action)

        when:
        def result = provider.get()

        then:
        result == task
        provider.present

        and:
        1 * taskFactory.create("task", DefaultTask) >> task
        1 * action.execute(task)
        0 * action._

        when:
        provider.get()

        then:
        0 * _
    }

    void "defined task is created and configured when queried by name"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.createLater("task", DefaultTask, action)

        when:
        def result = container.getByName("task")

        then:
        result == task

        and:
        1 * taskFactory.create("task", DefaultTask) >> task
        1 * action.execute(task)
        0 * action._
    }

    void "defined task is created and configured when found by name"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.createLater("task", DefaultTask, action)

        when:
        def result = container.findByName("task")

        then:
        result == task

        and:
        1 * taskFactory.create("task", DefaultTask) >> task
        1 * action.execute(task)
        0 * action._
    }

    void "can locate defined task by type and name without triggering creation or configuration"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.createLater("task", DefaultTask, action)

        when:
        def provider = container.getByNameLater(Task, "task")

        then:
        !provider.present

        and:
        0 * _

        when:
        def result = provider.get()

        then:
        result == task

        and:
        1 * taskFactory.create("task", DefaultTask) >> task
        1 * action.execute(task)
        0 * action._
    }

    void "can configure a task by type and name without triggering creation or configuration"() {
        def action = Mock(Action)
        def deferredAction = Mock(Action)
        def task = task("task")

        given:
        container.createLater("task", DefaultTask, action)

        when:
        def provider = container.getByNameLater(Task, "task")
        and:
        provider.configure(deferredAction)
        then:
        !provider.present

        and:
        0 * _

        when:
        def result = provider.get()

        then:
        result == task
        1 * taskFactory.create("task", DefaultTask) >> task
        then:
        1 * action.execute(task)
        then:
        1 * deferredAction.execute(task)
        then:
        0 * action._
        0 * deferredAction._
    }

    void "can locate task that already exists by type and name without triggering creation or configuration"() {
        def task = task("task")

        given:
        _ * taskFactory.create("task", DefaultTask, []) >> task
        container.create("task")

        when:
        def provider = container.getByNameLater(Task, "task")

        then:
        provider.present
        provider.get() == task
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
        def task = task("task")

        taskFactory.create("task", DefaultTask) >> task

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
        def task = task("task", CustomTask)

        taskFactory.create("task", CustomTask) >> task

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
        task(name, DefaultTask)
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
        1 * taskFactory.create(name, DefaultTask) >> task
        container.create(options)
        return task
    }

    private <U extends Task> U addTask(String name, Class<U> type) {
        def task = task(name, type)
        def options = [name: name, type: type]
        1 * taskFactory.create(name, type) >> task
        container.create(options)
        return task
    }

    interface CustomTask extends TaskInternal {}
}
