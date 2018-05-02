/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.execution.Cancellable
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.initialization.BuildCancellationToken
import spock.lang.Specification
import spock.lang.Subject

class CancellableTaskExecuterTest extends Specification {
    private TaskExecuter delegate = Mock(TaskExecuter)
    private BuildCancellationToken cancellationToken = Mock(BuildCancellationToken)
    private TaskStateInternal state = Mock(TaskStateInternal)
    private TaskExecutionContext context = Mock(TaskExecutionContext)
    @Subject
    private CancellableTaskExecuter executer = new CancellableTaskExecuter(delegate, cancellationToken)
    private TaskInternal cancellableTask = Mock(CancellableTask)
    private TaskInternal uncancellableTask = Mock(TaskInternal)

    def 'can set and clean callback when executing cancellable tasks'() {
        when:
        executer.execute(cancellableTask, state, context)

        then:
        1 * cancellationToken.addCallback(_)

        and:
        1 * delegate.execute(cancellableTask, state, context)

        and:
        1 * cancellationToken.removeCallback(_)
        0 * _._
    }

    def 'uncancellable tasks are executed directly'() {
        when:
        executer.execute(uncancellableTask, state, context)

        then:
        1 * delegate.execute(uncancellableTask, state, context)
        0 * _._
    }


    interface CancellableTask extends Cancellable, TaskInternal {}
}
