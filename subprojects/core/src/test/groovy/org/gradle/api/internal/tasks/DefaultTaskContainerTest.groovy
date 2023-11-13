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
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Rule
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.AbstractPolymorphicDomainObjectContainerSpec
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.internal.project.taskfactory.TaskInstantiator
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.util.Path
import org.gradle.util.TestUtil

import static java.util.Collections.singletonMap

class DefaultTaskContainerTest extends AbstractPolymorphicDomainObjectContainerSpec<Task> {

    private taskIdentityFactory = TestTaskIdentities.factory()
    private taskFactory = Mock(ITaskFactory)
    private project = Mock(ProjectInternal, name: "<project>") {
        identityPath(_) >> { String name ->
            Path.path(":project").child(name)
        }
        projectPath(_) >> { String name ->
            Path.path(":project").child(name)
        }
        getGradle() >> Mock(GradleInternal) {
            getIdentityPath() >> Path.path(":")
        }
        getOwner() >> Mock(ProjectState) {
            getDepth() >> 0
            getProjectPath() >> Path.path(":project")
        }
        getServices() >> Mock(ServiceRegistry)
        getTaskDependencyFactory() >> TestFiles.taskDependencyFactory()
        getObjects() >> Stub(ObjectFactory)
    } as ProjectInternal
    private container = new DefaultTaskContainerFactory(
        DirectInstantiator.INSTANCE,
        taskIdentityFactory,
        taskFactory,
        project as ProjectInternal,
        new TaskStatistics(),
        buildOperationExecutor,
        new BuildOperationCrossProjectConfigurator(buildOperationExecutor),
        callbackActionDecorator
    ).create()

    boolean supportsBuildOperations = true

    @Override
    final PolymorphicDomainObjectContainer<Task> getContainer() {
        return container
    }

    boolean externalProviderAllowed = false
    boolean directElementAdditionAllowed = false
    boolean elementRemovalAllowed = false

    @Override
    protected void addToContainer(Task element) {
        container.addInternal(element)
    }

    void 'cannot create task with no name'() {
        when:
        container.create([:])

        then:
        InvalidUserDataException e = thrown()
        e.message == "The task name must be provided."
    }

    void 'can create task with dependencies'() {
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

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
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        Task added = container.create([name: 'task', action: action])

        then:
        added == task
        1 * task.doFirst(action)
    }

    void 'can create task with Action closure'() {
        Closure action = Mock()
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        Task added = container.create([name: 'task', action: action])

        then:
        added == task
        1 * task.doFirst(action)
    }

    void 'can create task with description'() {
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        Task added = container.create([name: 'task', description: "some task"])

        then:
        added == task
        1 * task.setDescription("some task")
    }

    void 'can create task with group'() {
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        Task added = container.create([name: 'task', group: "some group"])

        then:
        added == task
        1 * task.setGroup("some group")
    }

    void "creates by Map"() {
        def options = singletonMap("name", "task")
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        def added = container.create(options)

        then:
        added == task
        container.getByName("task") == task
    }

    void "creates by name"() {
        given:
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

        expect:
        container.create("task") == task
        container.names.contains("task")
    }

    void "creates by name and type"() {
        given:
        def task = task("task", CustomTask)
        taskFactory.create(_ as TaskIdentity) >> task

        expect:
        container.create("task", CustomTask.class) == task
    }

    void "creates by name and closure"() {
        given:
        final Closure action = {}
        def task = task("task")

        taskFactory.create(_ as TaskIdentity) >> task

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

        taskFactory.create(_ as TaskIdentity) >> task

        when:
        def added = container.create("task", action)

        then:
        added == task
        1 * action.execute(task)
    }

    void "create by map wraps task creation failure"() {
        given:
        def failure = new RuntimeException()

        taskFactory.create(_ as TaskIdentity) >> { throw failure }

        when:
        container.create(name: "task")

        then:
        def e = thrown(GradleException)
        e.message == "Could not create task ':project:task'."
        e.cause == failure
    }

    void "create wraps task creation failure"() {
        given:
        def failure = new RuntimeException()

        taskFactory.create(_ as TaskIdentity) >> { throw failure }

        when:
        container.create("task")

        then:
        def e = thrown(GradleException)
        e.message == "Could not create task ':project:task'."
        e.cause == failure
    }

    void "create with action wraps task creation failure"() {
        given:
        def failure = new RuntimeException()
        def action = Mock(Action)

        taskFactory.create(_ as TaskIdentity) >> { throw failure }

        when:
        container.create("task", action)

        then:
        def e = thrown(GradleException)
        e.message == "Could not create task ':project:task'."
        e.cause == failure
    }

    void "create wraps task configuration failure"() {
        given:
        def failure = new RuntimeException()
        def action = Mock(Action)
        def task = task("task")

        taskFactory.create(_ as TaskIdentity) >> task
        action.execute(task) >> { throw failure }

        when:
        container.all(action)
        container.create("task", action)

        then:
        def e = thrown(GradleException)
        e.message == "Could not create task ':project:task'."
        e.cause == failure
    }

    void "replacing non-existent task by name throws exception"() {
        given:
        def task = task("task")
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        container.replace("task")

        then:
        def e = thrown(DefaultTaskContainer.TaskCreationException)
        e.cause.message == "Unnecessarily replacing a task that does not exist is not supported.  Use create() or register() directly instead.  You attempted to replace a task named 'task', but there is no existing task with that name."
    }

    void "replacing non-existent task by name and type throws exception"() {
        given:
        def task = task("task", CustomTask)
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        container.replace("task", CustomTask.class)

        then:
        def e = thrown(DefaultTaskContainer.TaskCreationException)
        e.cause.message == "Unnecessarily replacing a task that does not exist is not supported.  Use create() or register() directly instead.  You attempted to replace a task named 'task', but there is no existing task with that name."
    }

    void "does not fire rule when adding task"() {
        def rule = Mock(Rule)
        def task = task("task")

        container.addRule(rule)
        taskFactory.create(_ as TaskIdentity) >> task

        when:
        container.create("task")

        then:
        0 * rule._
    }

    void "prevents duplicate tasks"() {
        given:
        def task = addTask("task")
        1 * taskFactory.create(_ as TaskIdentity) >> { this.task("task") }

        when:
        container.create("task")

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Cannot add task 'task' as a task with that name already exists."
        container.getByName("task") == task
    }

    void "replacing existing duplicate task throws exception"() {
        given:
        addTask("task")
        def newTask = task("task")
        taskFactory.create(_ as TaskIdentity) >> newTask

        when:
        container.replace("task")

        then:
        def e = thrown(DefaultTaskContainer.TaskCreationException)
        e.cause.message == "Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('task')."
    }

    void "replaces registered task without realizing it"() {
        given:
        container.register("task")
        def newTask = task("task")

        when:
        def replaced = container.replace("task")

        then:
        noExceptionThrown()
        replaced == newTask
        container.getByName("task") == newTask

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> newTask
    }

    void "replaces unrealized registered task will execute configuration action against new task"() {
        given:
        def action = Mock(Action)
        def provider = container.register("task", action)
        provider.configure(action)
        container.configureEach(action)
        def newTask = task("task")

        when:
        container.replace("task")

        then:
        1 * taskFactory.create(_ as TaskIdentity) >> newTask
        3 * action.execute(newTask)
    }

    void "replaces registered task with compatible type"() {
        given:
        container.register("task", CustomTask)
        def newTask = task("task", MyCustomTask)

        when:
        def replaced = container.replace("task", MyCustomTask)

        then:
        noExceptionThrown()
        replaced == newTask

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> newTask
    }

    void "returns the same Task instance from TaskProvider after replace"() {
        given:
        def newTask = task("task")
        1 * taskFactory.create(_ as TaskIdentity) >> newTask

        when:
        def p1 = container.register("task")
        def p2 = container.named("task")
        def replaced = container.replace("task")

        then:
        p1.get() == p2.get()
        p1.get() == replaced
        p1.get() == container.getByName("task")
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

    void "does not find unknown tasks by path"() {
        when:
        expectTaskLookupInOtherProject(":other", "task", null)

        then:
        container.findByPath(":other:task") == null
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

        when:
        container.realize()

        then:
        0 * aTask.getTaskDependencies()
        0 * bTask.getTaskDependencies()
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

    void "can query task name and type from task provider after registration without realizing it"() {
        def action = Mock(Action)

        given:
        def provider = null
        container.configureEach(action)

        when:
        provider = container.register("a")

        then:
        provider.type == DefaultTask
        provider.name == "a"
        0 * action.execute(_)

        when:
        provider = container.register("b", Mock(Action))

        then:
        provider.type == DefaultTask
        provider.name == "b"
        0 * action.execute(_)

        when:
        provider = container.register("c", CustomTask)

        then:
        provider.type == CustomTask
        provider.name == "c"
        0 * action.execute(_)

        when:
        provider = container.register("d", CustomTask, Mock(Action))

        then:
        provider.type == CustomTask
        provider.name == "d"
        0 * action.execute(_)

        when:
        provider = container.register("e", CustomTask, "some", "constructor", "args")

        then:
        provider.type == CustomTask
        provider.name == "e"
        0 * action.execute(_)
    }

    void "can define task to create and configure later given name and type"() {
        def action = Mock(Action)

        when:
        def provider = container.register("task", DefaultTask, action)

        then:
        0 * taskFactory._

        and:
        container.names.contains("task")
        container.size() == 1
        !container.empty
        provider.present
    }

    void "can define task to create and configure later given name"() {
        def action = Mock(Action)
        def task = task("task")

        when:
        def provider = container.register("task", action)

        then:
        0 * taskFactory._

        and:
        container.names.contains("task")
        container.size() == 1

        when:
        def result = provider.get()

        then:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_)
        result == task
    }

    void "can define task to create later given name and type"() {
        when:
        def provider = container.register("task", DefaultTask)

        then:
        0 * taskFactory._

        and:
        container.names.contains("task")
        container.size() == 1
        !container.empty
        provider.present
    }

    void "can define task to create later given name"() {
        def task = task("task")

        when:
        def provider = container.register("task")

        then:
        0 * taskFactory._

        and:
        container.names.contains("task")
        container.size() == 1

        when:
        def result = provider.get()

        then:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        result == task
    }

    void "define task fails when task with given name already defined"() {
        given:
        1 * taskFactory.create(_ as TaskIdentity, _) >> task("task1")
        1 * taskFactory.create(_ as TaskIdentity, _) >> task("task2")

        container.create("task1")
        container.register("task2", {})

        when:
        container.register("task1", {})

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Cannot add task 'task1' as a task with that name already exists."

        when:
        container.register("task2", {})

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
        def provider = container.register("task", DefaultTask, action)

        when:
        def result = provider.get()

        then:
        result == task
        provider.present

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
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
        container.register("task", DefaultTask, action)

        when:
        def result = container.getByName("task")

        then:
        result == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(task)
        0 * action._
    }

    void "defined task is created and configured when found by name"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.register("task", DefaultTask, action)

        when:
        def result = container.findByName("task")

        then:
        result == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(task)
        0 * action._
    }

    void "container and task specific configuration actions are executed when task is created"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def action3 = Mock(Action)
        def action4 = Mock(Action)
        def action5 = Mock(Action)
        def action6 = Mock(Action)
        def task = task("task")

        given:
        container.configureEach(action1)
        def provider = container.register("task", DefaultTask, action2)
        container.configureEach(action3)
        provider.configure(action4)
        container.configureEach(action5)

        when:
        container.all(action6)

        then:
        1 * taskFactory.create(_ as TaskIdentity) >> task

        then:
        1 * action1.execute(task)

        then:
        1 * action2.execute(task)

        then:
        1 * action3.execute(task)

        then:
        1 * action4.execute(task)

        then:
        1 * action5.execute(task)

        then:
        1 * action6.execute(task)
        0 * action1._
        0 * action2._
        0 * action3._
        0 * action4._
        0 * action5._
        0 * action6._

        when:
        provider.get()

        then:
        0 * _
    }

    void "can locate defined task by type and name without triggering creation or configuration"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.register("task", DefaultTask, action)

        when:
        def provider = container.named("task")

        then:
        provider.present

        and:
        0 * _

        when:
        def result = provider.get()

        then:
        result == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(task)
        0 * action._
    }

    void "can configure a task by type and name without triggering creation or configuration"() {
        def action = Mock(Action)
        def deferredAction = Mock(Action)
        def task = task("task")

        given:
        container.register("task", DefaultTask, action)

        when:
        def provider = container.named("task")
        and:
        provider.configure(deferredAction)
        then:
        provider.present

        and:
        0 * _

        when:
        def result = provider.get()

        then:
        result == task
        1 * taskFactory.create(_ as TaskIdentity) >> task
        then:
        1 * action.execute(task)
        then:
        1 * deferredAction.execute(task)
        then:
        0 * action._
        0 * deferredAction._
    }

    void "task configuration action is executed immediately when task is already realized"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        1 * taskFactory.create(_ as TaskIdentity) >> task

        def provider = container.register("task")
        provider.get()

        when:
        provider.configure(action)

        then:
        1 * action.execute(task)
        0 * action._
    }

    void "fails task creation when creation rule throw exception"() {
        def action = Mock(Action)
        def task = task("task")

        when:
        container.create("task", DefaultTask, action)

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Failing creation rule"

        and:
        container.findByName("task") != null
        container.findByName("task") == task

        and:
        container.withType(DefaultTask).named("task").isPresent()
        container.withType(DefaultTask).named("task").get() == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing creation rule") }
    }

    void "fails later creation upon realizing through register provider when creation rule throw exception"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        def provider = container.register("task", DefaultTask, action)

        when:
        provider.get()

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing creation rule"

        and:
        provider.isPresent()

        and:
        container.findByName("task") != null
        container.findByName("task") == task

        and:
        container.withType(DefaultTask).named("task").isPresent()
        container.withType(DefaultTask).named("task").get() == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing creation rule") }

        when:
        provider.get()

        then:
        def ex2 = thrown(GradleException)
        ex2.is(ex)
        0 * _
    }

    void "fails later creation upon realizing through get() provider when creation rule throw exception"() {
        given:
        def action = Mock(Action)
        def task = task("task")
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing creation rule") }
        def creationProvider = container.register("task", DefaultTask, action)
        def provider = container.withType(DefaultTask).named("task")

        when:
        provider.get()

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing creation rule"

        and:
        provider.isPresent()

        and:
        container.findByName("task") != null
        container.findByName("task") == task

        and:
        creationProvider.isPresent()

        when:
        creationProvider.get() == task

        then:
        def ex2 = thrown(GradleException)
        ex2.is(ex)

        when:
        provider.get()

        then:
        def ex3 = thrown(GradleException)
        ex3.is(ex)
        0 * _
    }

    void "fails later creation upon realizing through register provider when task instantiation is unsuccessful"() {

        given:
        def action = Mock(Action)
        1 * taskFactory.create(_ as TaskIdentity) >> { throw new RuntimeException("Failing constructor") }
        0 * action.execute(_)

        def provider = container.register("task", DefaultTask, action)

        when:
        provider.get()

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing constructor"

        and:
        provider.isPresent()

        and:
        container.withType(DefaultTask).named("task").isPresent()

        and:
        container.named("task").isPresent()

        when:
        container.findByName("task")

        then:
        def ex2 = thrown(GradleException)
        ex2.is(ex)

        when:
        provider.getOrNull()

        then:
        def ex3 = thrown(GradleException)
        ex3.is(ex)
        0 * _
    }

    void "fails later creation upon realizing through get() provider when task instantiation is unsuccessful"() {
        given:
        def action = Mock(Action)
        1 * taskFactory.create(_ as TaskIdentity) >> { throw new RuntimeException("Failing constructor") }
        0 * action.execute(_)
        def creationProvider = container.register("task", DefaultTask, action)
        def provider = container.withType(DefaultTask).named("task")

        when:
        provider.get()

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing constructor"

        and:
        provider.isPresent()
        creationProvider.isPresent()

        when:
        container.findByName("task")

        then:
        def ex2 = thrown(GradleException)
        ex2.is(ex)

        when:
        provider.getOrNull()

        then:
        def ex3 = thrown(GradleException)
        ex3.is(ex)
        0 * _
    }

    void "fails later creation when task configuration via withType is unsuccessful"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.withType(DefaultTask, action)

        when:
        // The following throw an exception immediately because a failing eager configuration rule is registered
        container.register("task", DefaultTask)

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing withType configuration rule"

        and:
        container.findByName("task") != null
        container.findByName("task") == task

        and:
        container.withType(DefaultTask).named("task").isPresent()
        container.withType(DefaultTask).named("task").get() == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing withType configuration rule") }
    }

    void "fails task creation when task configuration via configureEach is unsuccessful"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.withType(DefaultTask).configureEach(action)

        when:
        container.create("task", DefaultTask)

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing configureEach configuration rule"

        and:
        container.findByName("task") != null
        container.findByName("task") == task

        and:
        container.withType(DefaultTask).named("task").isPresent()
        container.withType(DefaultTask).named("task").get() == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing configureEach configuration rule") }
    }

    void "fails later creation upon realizing through register provider when task configuration via configureEach is unsuccessful"() {
        def action = Mock(Action)
        def task = task("task")

        given:
        container.withType(DefaultTask).configureEach(action)
        def provider = container.register("task", DefaultTask)

        when:
        provider.get()

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing configureEach configuration rule"

        and:
        provider.isPresent()

        and:
        container.findByName("task") != null
        container.findByName("task") == task

        and:
        container.withType(DefaultTask).named("task").isPresent()
        container.withType(DefaultTask).named("task").get() == task

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing configureEach configuration rule") }

        when:
        provider.get()

        then:
        def ex2 = thrown(GradleException)
        ex2.is(ex)
        0 * _
    }

    void "fails later creation upon realizing through get() provider when task configuration via configureEach is unsuccessful"() {
        given:
        def action = Mock(Action)
        def task = task("task")
        1 * taskFactory.create(_ as TaskIdentity) >> task
        1 * action.execute(_) >> { throw new RuntimeException("Failing configureEach configuration rule") }

        container.withType(DefaultTask).configureEach(action)
        def creationProvider = container.register("task", DefaultTask)
        def provider = container.withType(DefaultTask).named("task")

        when:
        provider.get()
        then:
        def ex = thrown(GradleException)
        ex.message == "Could not create task ':project:task'."
        ex.cause.message == "Failing configureEach configuration rule"
        and:
        provider.isPresent()
        creationProvider.isPresent()
        container.findByName("task") == task

        when:
        creationProvider.get()
        then:
        def ex2 = thrown(GradleException)
        ex2.is(ex)

        when:
        provider.get()
        then:
        def ex3 = thrown(GradleException)
        ex3.is(ex)

        0 * _
    }

    void "can locate task that already exists by name"() {
        def task = task("task")

        given:
        _ * taskFactory.create(_ as TaskIdentity, []) >> task
        container.create("task")

        when:
        def provider = container.named("task")

        then:
        provider.present
        provider.get() == task
    }

    void "configuration action is executed eagerly for a task that already exists located by name"() {
        def task = task("task")
        def action = Mock(Action)

        given:
        _ * taskFactory.create(_ as TaskIdentity, []) >> task
        container.create("task")

        when:
        def provider = container.named("task")
        provider.configure(action)

        then:
        1 * action.execute(task)
        0 * action._
    }

    void "maybeCreate creates new task"() {
        given:
        def task = task("task")

        taskFactory.create(_ as TaskIdentity) >> task

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

        taskFactory.create(_ as TaskIdentity) >> task

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

    void "get() fails if unknown task is requested"() {
        when:
        container.withType(DefaultTask).named("unknown")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'unknown' not found in Mock for type 'ProjectInternal' named '<project>'."
    }

    void "get() fails if eagerly created task type is not a subtype"() {
        given:
        taskFactory.create(_ as TaskIdentity) >> task("task")
        container.create("task", DefaultTask)

        when:
        container.withType(CustomTask).named("task")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'task' not found in Mock for type 'ProjectInternal' named '<project>'."
    }

    void "get() fails if lazily created task type is not a subtype"() {
        container.register("task", DefaultTask)

        when:
        container.withType(CustomTask).named("task")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'task' not found in Mock for type 'ProjectInternal' named '<project>'."
        0 * taskFactory.create(_ as TaskIdentity)
    }

    void "can get() for eagerly created task subtype"() {
        given:
        taskFactory.create(_ as TaskIdentity) >> task("task")
        container.create("task", CustomTask)

        when:
        container.named("task")

        then:
        noExceptionThrown()
    }

    void "can get() for lazily created task subtype"() {
        container.register("task", CustomTask)

        when:
        container.named("task")

        then:
        noExceptionThrown()
        0 * taskFactory.create(_ as TaskIdentity)
    }

    void "can get() if task is eagerly created before"() {
        given:
        taskFactory.create(_ as TaskIdentity) >> task("task")
        container.create("task", DefaultTask)

        when:
        container.withType(DefaultTask).named("task")

        then:
        noExceptionThrown()
    }

    void "can get() if task is lazily created before"() {
        given:
        container.register("task", DefaultTask)

        when:
        container.withType(DefaultTask).named("task")

        then:
        noExceptionThrown()
        0 * taskFactory.create(_ as TaskIdentity)
    }

    void "overwriting eagerly created task throws exception"() {
        given:
        def customTask = task("task", CustomTask)
        1 * taskFactory.create(_ as TaskIdentity, _ as Object[]) >> customTask
        1 * taskFactory.create(_ as TaskIdentity, _ as Object[]) >> task("task", DefaultTask)
        container.create("task", CustomTask)

        when:
        container.withType(DefaultTask).named("task")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'task' not found in Mock for type 'ProjectInternal' named '<project>'."

        when:
        container.create([name: "task", type: DefaultTask, overwrite: true])

        then:
        def e = thrown(DefaultTaskContainer.TaskCreationException)
        e.cause.message == "Replacing an existing task that may have already been used by other plugins is not supported.  Use a different name for this task ('task')."
    }

    void "can get() if lazy created task gets overwrite"() {
        given:
        container.register("task", CustomTask)

        when:
        container.withType(MyCustomTask).named("task")

        then:
        def ex = thrown(UnknownTaskException)
        ex.message == "Task with name 'task' not found in Mock for type 'ProjectInternal' named '<project>'."
        0 * taskFactory.create(_ as TaskIdentity)

        when:
        container.create([name: "task", type: MyCustomTask, overwrite: true])
        container.withType(MyCustomTask).named("task")

        then:
        noExceptionThrown()
        1 * taskFactory.create(_ as TaskIdentity) >> task("task", MyCustomTask)
    }

    def "cannot add a provider directly to the task container"() {
        given:
        def provider = Mock(Provider) {
            _ * get() >> task("foo")
        }

        when:
        container.addLater(provider)

        then:
        thrown(UnsupportedOperationException)
    }

    def "cannot add a task directly to the task container"() {
        given:
        def task = task("foo")

        when:
        container.add(task)

        then:
        thrown(UnsupportedOperationException)
    }

    def "cannot add a collection of tasks directly to the task container"() {
        given:
        def task1 = task("foo")
        def task2 = task("bar")
        def task3 = task("baz")


        when:
        container.addAll([task1, task2, task3])

        then:
        thrown(UnsupportedOperationException)
    }

    def factory = new TaskInstantiator(taskIdentityFactory, new TaskFactory().createChild(project, TestUtil.instantiatorFactory().decorateScheme()), project)
    SomeTask a = factory.create("a", SomeTask)
    SomeTask b = factory.create("b", SomeTask)
    SomeTask c = factory.create("c", SomeTask)
    SomeOtherTask d = factory.create("d", SomeOtherTask)

    static class SomeTask extends DefaultTask {}

    static class SomeOtherTask extends DefaultTask {}

    Class<SomeTask> type = SomeTask
    Class<SomeOtherTask> otherType = SomeOtherTask

    @Override
    void setupContainerDefaults() {
        taskFactory.create(_ as TaskIdentity) >> { args ->
            def taskIdentity = args[0]
            task(taskIdentity.name)
        }
    }

    @Override
    List<Task> iterationOrder(Task... elements) {
        return elements.sort { it.name }
    }

    def "removing realized task throws exception"() {
        given:
        def task = addTask("a")

        when:
        container.remove(task)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead."
    }

    def "removing collection of tasks throws exception"() {
        given:
        def task1 = addTask("a")
        def task2 = addTask("b")
        def task3 = addTask("c")

        when:
        container.removeAll([task1, task2, task3])

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead."
    }

    def "removing registered providers throws exception"() {
        given:
        def provider1 = container.register("a", type)

        when:
        container.remove(provider1)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead."

        and:
        0 * taskFactory.create(_ as TaskIdentity)
    }

    def "clearing container throws exception"() {
        given:
        container.register("a", type)
        container.register("b", type)
        addTask("task")

        when:
        container.clear()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead."

        and:
        container.size() == 3
    }

    def "retainAll throws exception"() {
        given:
        def provider = container.register("a", type)
        container.register("b", type)

        when:
        container.retainAll([provider.get()])

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead."

        and:
        1 * taskFactory.create(_ as TaskIdentity) >> a

        and:
        container.size() == 2
    }

    def "will realize all register provider when querying the iterator"() {
        given:
        def provider1 = container.register("a", type)
        def provider2 = container.register("d", otherType)

        when:
        container.withType(type).iterator()

        then:
        1 * taskFactory.create(_ as TaskIdentity) >> a
    }

    def "removing register provider using iterator throws exception"() {
        1 * taskFactory.create(_ as TaskIdentity) >> a
        1 * taskFactory.create(_ as TaskIdentity) >> b
        def action = Mock(Action)

        given:
        container.register("a", type)
        container.register("b", type)

        when:
        def iterator = container.iterator()
        println iterator.next()
        iterator.remove()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Removing tasks from the task container is not supported.  Disable the tasks or use replace() instead."

        and:
        container.size() == 2
    }

    def "name based filtering does not realize pending"() {
        given: "a few realized tasks"
        1 * taskFactory.create(_ as TaskIdentity, _) >> task("r1")
        container.create("r1")

        1 * taskFactory.create(_ as TaskIdentity, _) >> task("r2")
        container.create("r2")

        1 * taskFactory.create(_ as TaskIdentity, _) >> task("r3")
        container.create("r3")

        and: "a few registered task"
        def action = Mock(Action)
        container.configureEach(action)

        container.register("t1")
        container.register("t2")
        container.register("t3")

        when: "name based filtering is applied"
        def filtered = container.named { !it.contains("2") }

        then: "the right task are filtered out"
        filtered.names.toList() == ["r1", "r3", "t1", "t3"]

        and: "no registered tasks get realized"
        0 * action.execute(_)

        when: "the filtered collection is iterated"
        1 * taskFactory.create(_ as TaskIdentity, _) >> task("t1")
        1 * taskFactory.create(_ as TaskIdentity, _) >> task("t3")
        filtered.toList()

        then: "filtered out registered tasks aren't realized"
        2 * action.execute(_)
    }

    private ProjectInternal expectTaskLookupInOtherProject(final String projectPath, final String taskName, def task) {
        def otherProject = Mock(ProjectInternal)
        def otherTaskContainer = Mock(TaskContainerInternal)
        def otherProjectState = Mock(ProjectState)

        project.findProject(projectPath) >> otherProject

        otherProject.owner >> otherProjectState
        1 * otherProjectState.ensureTasksDiscovered()
        otherProject.tasks >> otherTaskContainer

        otherTaskContainer.findByName(taskName) >> task

        otherProject
    }

    private TaskInternal task(final String name) {
        task(name, DefaultTask)
    }

    private <U extends TaskInternal> U task(final String name, Class<U> type) {
        def taskId = taskIdentityFactory.create(name, type, project)
        Mock(type, name: "[task" + taskId.id + "]") {
            getName() >> name
            getTaskDependency() >> Mock(TaskDependency)
            getTaskIdentity() >> taskId
        } as U
    }

    private Task addTask(String name) {
        def task = task(name)
        def options = singletonMap(Task.TASK_NAME, name)
        1 * taskFactory.create(_ as TaskIdentity) >> task
        container.create(options)
        return task
    }

    private <U extends Task> U addTask(String name, Class<U> type) {
        def task = task(name, type)
        def options = [name: name, type: type]
        1 * taskFactory.create(_ as TaskIdentity) >> task
        container.create(options)
        return task
    }

    interface CustomTask extends TaskInternal {}

    interface MyCustomTask extends CustomTask {}
}
