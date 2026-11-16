/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.Task
import spock.lang.Specification

class TaskDependencyUtilTest extends Specification {

    private final Task task = Mock()
    private final Task target = Mock()

    def "getDependencies resolves empty container without visiting it twice"() {
        def dependencies = Mock(TaskDependencyContainer)

        when:
        def tasks = TaskDependencyUtil.getDependencies(task, dependencies)

        then:
        1 * dependencies.visitDependencies(_)
        tasks.isEmpty()
    }

    def "getDependencies resolves recorded dependencies"() {
        def dependencies = Mock(TaskDependencyContainer)
        def nested = Mock(TaskDependencyContainer)

        when:
        def tasks = TaskDependencyUtil.getDependencies(task, dependencies)

        then:
        1 * dependencies.visitDependencies(_) >> { TaskDependencyResolveContext context ->
            assert context.task == task
            context.add(nested)
        }
        1 * nested.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(target) }
        tasks == [target] as Set
    }

    def "getDependencies rethrows visited failures"() {
        def dependencies = Mock(TaskDependencyContainer)
        def failure = new RuntimeException("broken")

        when:
        TaskDependencyUtil.getDependencies(task, dependencies)

        then:
        1 * dependencies.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.visitFailure(failure) }
        def e = thrown(TaskDependencyResolveException)
        e.message == "Could not determine the dependencies of ${task}."
        e.cause == failure
    }

}
