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

import org.gradle.api.Buildable
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

class CachingTaskDependencyResolveContextTest extends Specification {
    private final CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext([WorkDependencyResolver.TASK_AS_TASK])
    private final Task task = Mock()
    private final Task target = Mock()
    private final TaskDependencyContainerInternal dependency = Mock(TaskDependencyContainerInternal)

    def determinesTaskDependenciesByResolvingDependencyObjectForTask() {
        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_)
        tasks.isEmpty()
    }

    def resolvesTaskObject() {
        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(target) }
        tasks == [target] as LinkedHashSet
    }

    def resolvesTaskDependency() {
        TaskDependency otherDependency = Mock()

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherDependency) }
        1 * otherDependency.getDependencies(task) >> { [target] as Set }
        tasks == [target] as LinkedHashSet
    }

    def resolvesTaskDependencyInternal() {
        TaskDependencyInternal otherDependency = Mock()

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherDependency) }
        1 * otherDependency.getDependenciesForInternalUse(_) >> { [target] as Set }
        tasks == [target] as LinkedHashSet
    }

    def resolvesTaskDependencyContainer() {
        TaskDependencyContainer otherDependency = Mock()

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherDependency) }
        1 * otherDependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(target) }
        tasks == [target] as LinkedHashSet
    }

    def resolvesBuildable() {
        Buildable buildable = Mock()
        TaskDependency otherDependency = Mock()

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(buildable) }
        1 * buildable.getBuildDependencies() >> { otherDependency }
        1 * otherDependency.getDependencies(task) >> { [target] as Set }
        tasks == [target] as LinkedHashSet
    }

    def resolvesBuildableWithInternalTaskDependency() {
        Buildable buildable = Mock()
        TaskDependency otherDependency = Mock(TaskDependencyContainerInternal)

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(buildable) }
        1 * buildable.getBuildDependencies() >> { otherDependency }
        1 * otherDependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(target) }
        tasks == [target] as LinkedHashSet
    }

    def throwsExceptionForUnresolvableObject() {
        when:
        context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { args -> args[0].add('unknown') }
        def e = thrown(GradleException)
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Cannot resolve object of unknown type String to a Task."
    }

    def cachesResultForTaskDependency() {
        TaskDependencyInternal otherDependency = Mock(TaskDependencyContainerInternal)
        TaskDependencyInternal otherDependency2 = Mock()

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(otherDependency)
            context.add(otherDependency2)
        }
        1 * otherDependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherDependency2) }
        1 * otherDependency2.getDependenciesForInternalUse(task) >> { [target] as Set }
        tasks == [target] as LinkedHashSet
    }

    def cachesResultForTaskDependencyInternal() {
        TaskDependencyInternal otherDependency = Mock(TaskDependencyContainerInternal)
        TaskDependencyInternal otherDependency2 = Mock(TaskDependencyContainerInternal)

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(otherDependency)
            context.add(otherDependency2)
        }
        1 * otherDependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(otherDependency2) }
        1 * otherDependency2.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(target) }
        tasks == [target] as LinkedHashSet
    }

    def cachesResultForBuildable() {
        TaskDependencyInternal otherDependency = Mock(TaskDependencyContainerInternal)
        Buildable buildable = Mock()
        TaskDependency otherDependency2 = Mock()

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context
            context.add(otherDependency)
            context.add(buildable)
        }
        1 * otherDependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(buildable) }
        1 * buildable.getBuildDependencies() >> otherDependency2
        1 * otherDependency2.getDependencies(task) >> { [target] as Set }
        tasks == [target] as LinkedHashSet
    }

    def cachesResultForBuildableInternal() {
        TaskDependencyInternal otherDependency = Mock(TaskDependencyContainerInternal)
        Buildable buildable = Mock()
        TaskDependencyInternal otherDependency2 = Mock(TaskDependencyContainerInternal)

        when:
        def tasks = context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            context.add(otherDependency)
            context.add(buildable)
        }
        1 * otherDependency.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(buildable) }
        1 * buildable.getBuildDependencies() >> otherDependency2
        1 * otherDependency2.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(target) }
        tasks == [target] as LinkedHashSet
    }

    def wrapsFailureToResolveTask() {
        def failure = new RuntimeException()

        when:
        context.getDependencies(task, dependency)

        then:
        1 * dependency.visitDependencies(_) >> { throw failure }
        def e = thrown(TaskDependencyResolveException)
        e.message == "Could not determine the dependencies of $task."
        e.cause == failure
    }

    def failsWhenThereIsACyclicDependency() {
        throw new UnsupportedOperationException()
    }

    def providesSomeWayToIndicateThatResultIsSpecificToTheResolvedTask() {
        throw new UnsupportedOperationException()
    }
}

