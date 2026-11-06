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

class RecordingTaskDependencyResolveContextTest extends Specification {

    private final Task task = Mock()
    private final Task target = Mock()

    def "is empty when nothing is recorded"() {
        def recording = new RecordingTaskDependencyResolveContext(task)
        def context = Mock(TaskDependencyResolveContext)

        when:
        recording.visitDependencies(context)

        then:
        recording.isEmpty()
        recording.task == task
        0 * context._
    }

    def "replays dependencies and failures in order"() {
        def recording = new RecordingTaskDependencyResolveContext(null)
        def container = Mock(TaskDependencyContainer)
        def failure = new RuntimeException("broken")
        def context = Mock(TaskDependencyResolveContext)

        when:
        recording.add(target)
        recording.visitFailure(failure)
        recording.add(container)

        then:
        !recording.isEmpty()

        when:
        recording.visitDependencies(context)

        then:
        1 * context.add(target)

        then:
        1 * context.visitFailure(failure)

        then:
        1 * context.add(container)
        0 * context._
    }

}
