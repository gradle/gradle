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

import org.gradle.api.execution.TaskActionListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class ActionEventFiringTaskExecutorTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def publicListener = Mock(TaskActionListener)
    def internalListener = Mock(TaskOutputChangesListener)
    def executer = new ActionEventFiringTaskExecuter(delegate, internalListener, publicListener)

    def task = Stub(TaskInternal)
    def state = new TaskStateInternal()
    def executionContext = Stub(TaskExecutionContext)


    def "notifies listeners before and after task execution"() {
        when:
        executer.execute(task, state, executionContext)

        then:
        1 * publicListener.beforeActions(task)

        then:
        1 * delegate.execute(task, state, executionContext)

        then:
        1 * publicListener.afterActions(task)
    }

    def "notifies listeners even if delegate fails"() {
        when:
        executer.execute(task, state, executionContext)

        then:
        1 * publicListener.beforeActions(task)

        then:
        1 * delegate.execute(task, state, executionContext) >> { throw new IllegalStateException() }

        then:
        1 * publicListener.afterActions(task)

        and:
        thrown IllegalStateException
    }
}
