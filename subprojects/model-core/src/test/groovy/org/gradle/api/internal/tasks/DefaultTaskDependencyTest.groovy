/*
 * Copyright 2007, 2008 the original author or authors.
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

import org.gradle.api.Buildable
import org.gradle.api.Task
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.util.TextUtil
import spock.lang.Specification

import java.util.concurrent.Callable

import static org.gradle.util.WrapUtil.toSet

class DefaultTaskDependencyTest extends Specification {
    private final TaskResolver resolver = Mock(TaskResolver.class)
    private final DefaultTaskDependency dependency = new DefaultTaskDependency(resolver)
    private Task task
    private Task otherTask

    def setup() {
        task = Mock(Task)
        otherTask = Mock(Task)
    }

    def "has no dependencies by default"() {
        expect:
        dependency.getDependencies(task) == emptySet()
    }

    def "can depend on char sequence"() {
        def input = new StringBuilder("other")

        given:
        1 * resolver.resolveTask("other") >> otherTask

        when:
        dependency.add(input)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on a task instance"() {
        when:
        dependency.add(otherTask)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on a task dependency"() {
        def otherDependency = Mock(TaskDependency)

        given:
        1 * otherDependency.getDependencies(task) >> toSet(otherTask)

        when:
        dependency.add(otherDependency);

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on a closure"() {
        when:
        dependency.add({ Task suppliedTask ->
            assert suppliedTask == task
            otherTask
        })

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on a closure that returns null"() {
        when:
        dependency.add({ null })

        then:
        dependency.getDependencies(task) == emptySet()
    }

    def "can depend on a buildable"() {
        Buildable buildable = Mock(Buildable)
        TaskDependency otherDependency = Mock(TaskDependency)

        given:
        1 * buildable.getBuildDependencies() >> otherDependency
        1 * otherDependency.getDependencies(task) >> toSet(otherTask)

        when:
        dependency.add(buildable)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on an iterable"() {
        List tasks = [otherTask]
        Iterable iterable = { tasks.iterator() } as Iterable

        when:
        dependency.add(iterable)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on a callable"() {
        Callable callable = Mock(Callable)

        given:
        1 * callable.call() >> otherTask

        when:
        dependency.add(callable)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can depend on a callable that returns null"() {
        Callable callable = Mock(Callable)

        given:
        1 * callable.call() >> null

        when:
        dependency.add(callable)

        then:
        dependency.getDependencies(task) == emptySet()
    }

    def "delegates to Provider to determine build dependencies"() {
        def provider = Mock(ProviderInternal)

        given:
        1 * provider.maybeVisitBuildDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherTask); return true }

        when:
        dependency.add(provider)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "uses Provider value to determine build dependencies when Provider does not know anything about the tasks that produce its value"() {
        def provider = Mock(ProviderInternal)

        given:
        1 * provider.maybeVisitBuildDependencies(_) >> { return false }
        1 * provider.get() >> otherTask

        when:
        dependency.add(provider)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "delegates to TaskDependencyContainer to determine build dependencies"() {
        def dep = Mock(TaskDependencyContainer)

        given:
        1 * dep.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherTask) }

        when:
        dependency.add(dep)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "delegates to nested TaskDependencyContainer to determine build dependencies"() {
        def dep = Mock(TaskDependencyContainer)
        def nested = Mock(TaskDependencyContainer)

        given:
        1 * dep.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(nested) }
        1 * nested.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherTask) }

        when:
        dependency.add(dep)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "fails for other types"() {
        when:
        dependency.add(12)
        dependency.getDependencies(task)

        then:
        def e = thrown(TaskDependencyResolveException)
        e.cause instanceof UnsupportedNotationException
        e.cause.message == TextUtil.toPlatformLineSeparators('''Cannot convert 12 to a task.
The following types/formats are supported:
  - A String or CharSequence task name or path
  - A Task instance
  - A TaskReference instance
  - A Buildable instance
  - A TaskDependency instance
  - A Provider that represents a task output
  - A Provider instance that returns any of these types
  - A Closure instance that returns any of these types
  - A Callable instance that returns any of these types
  - An Iterable, Collection, Map or array instance that contains any of these types''')
    }

    def "fails for char sequence when no resolver provided"() {
        DefaultTaskDependency dependency = new DefaultTaskDependency()
        StringBuffer dep = new StringBuffer("task")

        when:
        dependency.add(dep)
        dependency.getDependencies(task)

        then:
        def e = thrown(TaskDependencyResolveException)
        e.cause instanceof UnsupportedNotationException
        e.cause.message.startsWith "Cannot convert $dep to a task."
    }

    def "flattens collections"() {
        when:
        dependency.add(toSet(otherTask))

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "flattens maps"() {
        when:
        dependency.add([key: otherTask])

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "flattens arrays"() {
        when:
        dependency.add([[otherTask] as Task[]])

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    def "can mutate dependency values by removing a Task instance from dependency"() {
        given:
        dependency.add(otherTask)

        when:
        dependency.mutableValues.remove(otherTask)

        then:
        dependency.getDependencies(task) == toSet()
    }

    def "can mutate dependency values by removing a Task instance from dependency containing a Provider to the Task instance"() {
        given:
        otherTask.name >> "otherTask"

        def provider = Mock(TestTaskProvider)
        provider.type >> otherTask.class
        provider.name >> otherTask.name
        dependency.add(provider)

        when:
        dependency.mutableValues.remove(otherTask)

        then:
        dependency.getDependencies(task) == toSet()
    }

    def "can mutate dependency values by removing a Provider instance from dependency containing the Provider instance"() {
        given:
        def provider = Mock(TestTaskProvider)
        dependency.add(provider)

        when:
        dependency.mutableValues.remove(provider)

        then:
        dependency.getDependencies(task) == toSet()
    }

    def "can nest iterables and maps and closures and callables"() {
        Map nestedMap = [task: otherTask]
        Iterable nestedCollection = [nestedMap]
        Callable nestedCallable = { nestedCollection } as Callable
        Closure nestedClosure = { nestedCallable }
        List collection = [nestedClosure]
        Closure closure = { collection }
        Object[] array = [closure] as Object[]
        Map map = [key: array]
        Callable callable = { map } as Callable

        when:
        dependency.add(callable)

        then:
        dependency.getDependencies(task) == toSet(otherTask)
    }

    static emptySet() {
        return [] as Set
    }

    interface TestTaskProvider extends ProviderInternal, TaskProvider {
    }
}
