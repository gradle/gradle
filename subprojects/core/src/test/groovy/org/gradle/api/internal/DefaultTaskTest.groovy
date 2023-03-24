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

package org.gradle.api.internal

import com.google.common.collect.Lists
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.taskfactory.TestTaskIdentities
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.internal.Actions
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.slf4j.ContextAwareTaskLogger
import org.gradle.util.TestUtil
import spock.lang.Issue

import java.util.concurrent.Callable

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn

class DefaultTaskTest extends AbstractTaskTest {
    ClassLoader cl
    DefaultTask defaultTask

    Object testCustomPropValue

    def setup() {
        testCustomPropValue = new Object()
        defaultTask = createTask(DefaultTask.class)
        cl = Thread.currentThread().contextClassLoader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = cl
    }

    DefaultTask getTask() {
        defaultTask
    }

    def "default task"() {
        given:
        def identity = TestTaskIdentities.create(TEST_TASK_NAME, Task, project)
        Task task = AbstractTask.injectIntoNewInstance(project, identity, { TestUtil.newInstance(DefaultTask) } as Callable)

        expect:
        task.dependsOn.isEmpty()
        task.actions == []
        (task as TaskInternal).taskIdentity == identity
    }

    def "useful toString()"() {
        expect:
        'task \':testTask\'' == task.toString()
    }

    def "can inject values into task when using no-args constructor"() {
        given:
        def identity = TestTaskIdentities.create(TEST_TASK_NAME, Task, project)
        def task = AbstractTask.injectIntoNewInstance(project, identity, { TestUtil.newInstance(DefaultTask) } as Callable)

        expect:
        task.project.is(project)
        task.name == TEST_TASK_NAME
        (task as TaskInternal).taskIdentity == identity
    }

    def "dependsOn() works"() {
        given:
        def dependsOnTask = createTask(project, "somename")
        def task = createTask(project, TEST_TASK_NAME)
        project.getTasks().create("path1")
        project.getTasks().create("path2")

        when:
        task.dependsOn(Project.PATH_SEPARATOR + "path1")

        then:
        task dependsOn("path1")

        when:
        task.dependsOn("path2", dependsOnTask)

        then:
        task dependsOn("path1", "path2", "somename")
    }

    def "test mustRunAfter()"() {
        given:
        def mustRunAfterTask = createTask(project, "mustRunAfter")
        def mustRunAfterTaskUsingPath = project.getTasks().create("path")
        def task = createTask(project, TEST_TASK_NAME)
        task.mustRunAfter(mustRunAfterTask, "path")

        expect:
        task.mustRunAfter.getDependencies(task) == [mustRunAfterTask, mustRunAfterTaskUsingPath] as Set
    }

    def "test finalizedBy()"() {
        given:
        def finalizer = createTask(project, "finalizer")
        def finalizerFromPath = project.getTasks().create("path")
        def finalized = createTask(project, TEST_TASK_NAME)
        finalized.finalizedBy(finalizer, "path")

        expect:
        finalized.finalizedBy.getDependencies(finalized) == [finalizer, finalizerFromPath] as Set
    }

    def "test set finalizedBy()"() {
        given:
        def finalizer = createTask(project, "finalizer")
        def finalizerFromPath = project.getTasks().create("path")
        def finalized = createTask(project, TEST_TASK_NAME)
        finalized.finalizedBy = [finalizer, "path"]

        expect:
        finalized.finalizedBy.getDependencies(finalized) == [finalizer, finalizerFromPath] as Set
    }

    def "test shouldRunAfter()"() {
        given:
        def shouldRunAfterTask = createTask(project, "shouldRunAfter")
        def shouldRunAfterFromPath = project.getTasks().create("path")
        def task = createTask(project, TEST_TASK_NAME)
        task.shouldRunAfter(shouldRunAfterTask, shouldRunAfterFromPath)

        expect:
        task.shouldRunAfter.getDependencies(task) == [shouldRunAfterTask, shouldRunAfterFromPath] as Set
    }

    def "test set shouldRunAfter()"() {
        given:
        def shouldRunAfterTask = createTask(project, "shouldRunAfter")
        def shouldRunAfterFromPath = project.getTasks().create("path")
        def task = createTask(project, TEST_TASK_NAME)
        task.shouldRunAfter = [shouldRunAfterTask, shouldRunAfterFromPath]

        expect:
        task.shouldRunAfter.getDependencies(task) == [shouldRunAfterTask, shouldRunAfterFromPath] as Set
    }

    def "test configure()"() {
        given:
        def action1 = { Task t -> }

        expect:
        task.is(task.configure {
            doFirst(action1)
        })
        task.actions.size() == 1
    }

    def "doFirst() adds actions to the start of action list"() {
        given:
        Action<Task> action1 = Actions.doNothing()
        Action<Task> action2 = Actions.doNothing()

        expect:
        defaultTask.is(defaultTask.doFirst(action1))
        1 == defaultTask.actions.size()

        defaultTask.is(defaultTask.doFirst(action2))
        2 == defaultTask.actions.size()

        action2.is(defaultTask.actions[0].action)
        action1.is(defaultTask.actions[1].action)
    }

    def "doLast() adds action to the end of actions list"() {
        given:
        Action<Task> action1 = Actions.doNothing()
        Action<Task> action2 = Actions.doNothing()

        expect:
        defaultTask.is(defaultTask.doLast(action1))
        1 == defaultTask.actions.size()

        defaultTask.is(defaultTask.doLast(action2))
        2 == defaultTask.actions.size()

        action1.is(defaultTask.actions[0].action)
        action2.is(defaultTask.actions[1].action)
    }

    def "sets contextClassLoader when executing action"() {
        given:
        Action<Task> testAction = Mock(Action)

        when:
        defaultTask.doFirst(testAction)
        Thread.currentThread().contextClassLoader = new ClassLoader(getClass().classLoader) {}
        defaultTask.actions[0].execute(defaultTask)

        then:
        1 * testAction.execute(defaultTask) >> {
            assert Thread.currentThread().contextClassLoader.is(testAction.getClass().classLoader)
        }
    }

    def "closure action delegates to task"() {
        given:
        def testAction = {
            assert delegate == defaultTask
            assert resolveStrategy == Closure.DELEGATE_FIRST
        }

        expect:
        defaultTask.doFirst(testAction)
        defaultTask.actions[0].execute(defaultTask)
    }

    def "sets contextClassLoader when running closure action"() {
        given:
        def testAction = {
            assert Thread.currentThread().contextClassLoader.is(getClass().classLoader)
        }
        Thread.currentThread().contextClassLoader = new ClassLoader(getClass().classLoader) {}

        expect:
        defaultTask.doFirst(testAction)
        defaultTask.actions[0].execute(defaultTask)
    }

    def "doLast() with closure adds action to the end of actions list"() {
        given:
        def testAction1 = {}
        def testAction2 = {}
        def testAction3 = {}
        defaultTask.doLast(testAction1)
        defaultTask.doLast(testAction2)
        defaultTask.doLast(testAction3)

        expect:
        defaultTask.actions[0].closure.is(testAction1)
        defaultTask.actions[1].closure.is(testAction2)
        defaultTask.actions[2].closure.is(testAction3)
    }

    def "doFirst() with closure adds action to the start of actions list"() {
        given:
        def testAction1 = {}
        def testAction2 = {}
        def testAction3 = {}
        defaultTask.doFirst(testAction1)
        defaultTask.doFirst(testAction2)
        defaultTask.doFirst(testAction3)

        expect:
        defaultTask.actions[0].closure.is(testAction3)
        defaultTask.actions[1].closure.is(testAction2)
        defaultTask.actions[2].closure.is(testAction1)
    }

    @Issue("GRADLE-2774")
    def "add closure action to actions and execute"() {
        given:
        def actionExecuted = false
        def closureAction = { t -> actionExecuted = true } as Action
        defaultTask.actions.add(closureAction)

        when:
        execute(defaultTask)

        then:
        actionExecuted
    }

    @Issue("GRADLE-2774")
    def "addAll actions to actions and execute"() {
        given:
        def actionExecuted = false
        def closureAction = { t -> actionExecuted = true } as Action
        defaultTask.actions.addAll(Lists.newArrayList(closureAction))

        when:
        execute(defaultTask)

        then:
        actionExecuted
    }

    @Issue("GRADLE-2774")
    def "addAll actions to actions with index and execute"() {
        given:
        def actionExecuted = false
        def closureAction = { t -> actionExecuted = true } as Action
        defaultTask.actions.addAll(0, Lists.newArrayList(closureAction))

        when:
        execute(defaultTask)

        then:
        actionExecuted
    }

    @Issue("GRADLE-2774")
    def "addAll actions to actions with iterator and execute"() {
        given:
        def actionExecuted = false
        def closureAction = { t -> actionExecuted = true } as Action
        defaultTask.actions.listIterator().add(closureAction)

        when:
        execute(defaultTask)

        then:
        actionExecuted
    }

    def "added actions can be removed"() {
        given:
        def closureAction = { t -> } as Action

        when:
        defaultTask.actions.add(closureAction)

        then:
        defaultTask.actions.size() == 1

        when:
        defaultTask.actions.remove(closureAction)

        then:
        defaultTask.actions.isEmpty()

        when:
        defaultTask.actions.add(closureAction)

        then:
        defaultTask.actions.size() == 1

        when:
        defaultTask.actions.removeAll([closureAction])

        then:
        defaultTask.actions.isEmpty()
    }

    def "add null to actions throws"() {
        when:
        defaultTask.actions.add(null)

        then:
        thrown(InvalidUserDataException)
    }

    def "add null to actions with index throws"() {
        when:
        defaultTask.actions.add(0, null)

        then:
        thrown(InvalidUserDataException)
    }

    def "addAll null to actions throws"() {
        when:
        defaultTask.actions.addAll((Collection) null)

        then:
        thrown(InvalidUserDataException)
    }

    def "addAll null to actions with index throws"() {
        when:
        defaultTask.actions.addAll(0, null)

        then:
        thrown(InvalidUserDataException)
    }

    def "execute() throws TaskExecutionException"() {
        when:
        def failure = new RuntimeException()
        defaultTask.doFirst { throw failure }
        execute(defaultTask)

        then:
        RuntimeException actual = thrown()
        actual.cause.is(failure)
        defaultTask.state.failure instanceof TaskExecutionException
        defaultTask.state.failure.cause.is(failure)
    }

    def "get and set convention properties"() {
        given:
        def convention = new TestConvention()
        defaultTask.convention.plugins.test = convention

        expect:
        defaultTask.hasProperty('conventionProperty')

        when:
        defaultTask.conventionProperty = 'value'

        then:
        defaultTask.conventionProperty == 'value'
        convention.conventionProperty == 'value'
    }

    def "can call convention methods"() {
        given:
        defaultTask.convention.plugins.test = new TestConvention()

        expect:
        defaultTask.conventionMethod('a', 'b').toString() == "a.b"
    }

    def "accessing missing property throws"() {
        when:
        defaultTask."unknownProp"

        then:
        thrown(MissingPropertyException)
    }

    def "can get temporary directory"() {
        given:
        def tmpDir = new File(project.buildDir, "tmp/testTask")

        expect:
        !tmpDir.exists()
        defaultTask.temporaryDir == tmpDir
        tmpDir.isDirectory()
    }

    def "can access services"() {
        expect:
        defaultTask.services.get(ListenerManager) != null
    }

    def "unnamed task action are named similar by all action definition methods"() {
        given:
        Task taskWithActionActions = createTask(DefaultTask)
        Task taskWithCallableActions = createTask(DefaultTask)

        when:
        taskWithActionActions.doFirst(Mock(Action))
        taskWithCallableActions.doFirst {}
        taskWithActionActions.doLast(Mock(Action))
        taskWithCallableActions.doLast {}

        then:
        taskWithActionActions.actions[0].displayName == "Execute doFirst {} action"
        taskWithActionActions.actions[1].displayName == "Execute doLast {} action"
        taskWithActionActions.actions[0].displayName == taskWithCallableActions.actions[0].displayName
        taskWithActionActions.actions[1].displayName == taskWithCallableActions.actions[1].displayName
    }

    def "named task action are named similar by all action definition methods"() {
        given:
        Task taskWithActionActions = createTask(DefaultTask)
        Task taskWithCallableActions = createTask(DefaultTask)

        when:
        taskWithActionActions.doFirst("A first step", Mock(Action))
        taskWithCallableActions.doFirst("A first step") {}
        taskWithActionActions.doLast("One last thing", Mock(Action))
        taskWithCallableActions.doLast("One last thing") {}

        then:
        taskWithActionActions.actions[0].displayName == "Execute A first step"
        taskWithActionActions.actions[1].displayName == "Execute One last thing"
        taskWithActionActions.actions[0].displayName == taskWithCallableActions.actions[0].displayName
        taskWithActionActions.actions[1].displayName == taskWithCallableActions.actions[1].displayName
    }

    def "describable actions are not renamed"() {
        setup:
        def namedAction = Mock(InputChangesAwareTaskAction)
        namedAction.displayName >> "I have a name"

        when:
        task.actions.add(namedAction)
        task.actions.add(0, namedAction)
        task.doFirst(namedAction)
        task.doLast(namedAction)
        task.appendParallelSafeAction(namedAction)
        task.prependParallelSafeAction(namedAction)

        then:
        task.actions[0].displayName == "I have a name"
        task.actions[1].displayName == "I have a name"
        task.actions[2].displayName == "I have a name"
        task.actions[3].displayName == "I have a name"
        task.actions[4].displayName == "I have a name"
        task.actions[5].displayName == "I have a name"

    }

    def "unconventionally added actions that are not describable are unnamed"() {
        when:
        task.actions.add(Mock(Action))

        then:
        task.actions[0].displayName == "Execute unnamed action"
    }

    def "can detect tasks with custom actions added"() {
        expect:
        !task.hasCustomActions

        when:
        task.prependParallelSafeAction {}

        then:
        !task.hasCustomActions

        when:
        task.doFirst {}

        then:
        task.hasCustomActions
    }

    def "can rewrite task logger warnings"() {
        given:
        def rewriter = Mock(ContextAwareTaskLogger.MessageRewriter)

        when:
        task.logger.setMessageRewriter(rewriter)
        task.logger.warn("test")

        then:
        1 * rewriter.rewrite(LogLevel.WARN, "test")
    }
}

class TestConvention {
    def conventionProperty

    def conventionMethod(a, b) {
        "$a.$b"
    }
}
