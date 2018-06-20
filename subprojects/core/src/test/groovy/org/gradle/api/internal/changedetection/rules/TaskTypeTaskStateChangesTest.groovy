/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot
import org.gradle.api.internal.changedetection.state.TaskExecution
import org.gradle.api.internal.tasks.ContextAwareTaskAction
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.internal.Cast
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.util.Path
import spock.lang.Specification

class TaskTypeTaskStateChangesTest extends Specification {
    def taskLoaderHash = HashCode.fromInt(123)
    def taskLoader = SimpleTask.getClassLoader()
    def task = Stub(TaskInternal) {
        getIdentityPath() >> Path.path(":test")
    }
    def hasher = Mock(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(taskLoader) >> taskLoaderHash
    }

    TaskExecution mockExecution(ImplementationSnapshot impl, ImplementationSnapshot... actions) {
        return Mock(TaskExecution) {
            getTaskImplementation() >> impl
            getTaskActionImplementations() >> ImmutableList.copyOf(actions)
        }
    }

    def "up-to-date when task is the same"() {
        def previous = mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes.empty
    }

    def "not up-to-date when task name changed"() {
        def previous = mockExecution(impl(PreviousTask, taskLoaderHash), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' has changed type from '$PreviousTask.name' to '$SimpleTask.name'." as String]
    }

    def "not up-to-date when class-loader has changed"() {
        def previousHash = HashCode.fromInt(987)
        def previous =  mockExecution(impl(SimpleTask, previousHash), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' class path has changed from ${previousHash} to ${taskLoaderHash}." as String]
    }

    def "not up-to-date when action class-loader has changed"() {
        def previousHash = HashCode.fromInt(987)
        def previous =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, previousHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' has additional actions that have changed"]
    }

    def "not up-to-date when action is added"() {
        def previous =  mockExecution(impl(SimpleTask, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' has additional actions that have changed"]
    }

    def "not up-to-date when action is removed"() {
        def previous =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' has additional actions that have changed"]
    }

    def "not up-to-date when action with same class-loader is added"() {
        def previous =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' has additional actions that have changed"]
    }

    def "not up-to-date when task is loaded with an unknown classloader"() {
        def taskClassLoader = new GroovyClassLoader(getClass().getClassLoader())
        Class<? extends TaskInternal> simpleTaskClass = Cast.uncheckedCast(taskClassLoader.parseClass("""
            import org.gradle.api.*

            class SimpleTask extends DefaultTask {}
        """))

        def previous =  mockExecution(impl(simpleTaskClass, null), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(simpleTaskClass, null), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' was loaded with an unknown classloader"]
    }

    def "not up-to-date when task action is loaded with an unknown classloader"() {
        def previous =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, null))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' has an additional action that was loaded with an unknown classloader"]
    }

    def "not up-to-date when task was previously loaded with an unknown classloader"() {
        def previous =  mockExecution(impl(SimpleTask, null), impl(TestAction, taskLoaderHash))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' was loaded with an unknown classloader during the previous execution"]
    }

    def "not up-to-date when task action was previously loaded with an unknown classloader"() {
        def previous =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, null))
        def current =  mockExecution(impl(SimpleTask, taskLoaderHash), impl(TestAction, taskLoaderHash))

        def changes = collectChanges(new TaskTypeTaskStateChanges(previous, current, task))

        expect:
        changes == ["Task ':test' had an additional action that was loaded with an unknown classloader during the previous execution"]
    }

    List<String> collectChanges(TaskTypeTaskStateChanges stateChanges) {
        def visitor = new CollectingTaskStateChangeVisitor()
        stateChanges.accept(visitor)
        return visitor.changes*.message
    }

    private static ImplementationSnapshot impl(Class<?> type, HashCode classLoaderHash) {
        new ImplementationSnapshot(type.getName(), classLoaderHash)
    }

    private class SimpleTask extends DefaultTask {}
    private class PreviousTask extends DefaultTask {}
    private class SimpleAction implements Action<Void> {
        @Override
        void execute(Void value) {
        }
    }

    private static class TestAction implements ContextAwareTaskAction {
        final ClassLoader classLoader
        final String actionClassName

        TestAction() {
            this(TestAction, TestAction.classLoader)
        }

        TestAction(Class<?> actionType, ClassLoader classLoader) {
            this.actionClassName = actionType.name
            this.classLoader = classLoader
        }

        @Override
        void contextualise(TaskExecutionContext context) {
        }

        @Override
        void releaseContext() {
        }

        @Override
        void execute(Task task) {
        }

        @Override
        String getDisplayName() {
            return "Execute test action"
        }
    }
}
