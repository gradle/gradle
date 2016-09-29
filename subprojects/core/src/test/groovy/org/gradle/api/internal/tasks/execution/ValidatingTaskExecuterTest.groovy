/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskValidationException
import spock.lang.Specification

class ValidatingTaskExecuterTest extends Specification {
    final TaskExecuter target = Mock()
    final TaskInternal task = Mock()
    final TaskStateInternal state = Mock()
    final TaskExecutionContext executionContext = Mock()
    final TaskValidator validator = Mock()
    final ValidatingTaskExecuter executer = new ValidatingTaskExecuter(target)

    def executesTaskWhenThereAreNoViolations() {
        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.validators >> [validator]
        1 * validator.validate(task, !null)
        1 * target.execute(task, state, executionContext)
        0 * _
    }

    def failsTaskWhenThereIsAViolation() {
        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.validators >> [validator]
        1 * validator.validate(task, !null) >> { it[1] << 'failure' }
        1 * state.setOutcome(!null as Throwable) >> {
            def failure = it[0]
            assert failure instanceof TaskValidationException
            assert failure.message == "A problem was found with the configuration of $task."
            assert failure.cause instanceof InvalidUserDataException
            assert failure.cause.message == 'failure'
        }
        0 * _
    }

    def failsTaskWhenThereAreMultipleViolations() {
        when:
        executer.execute(task, state, executionContext)

        then:
        _ * task.validators >> [validator]
        1 * validator.validate(task, !null) >> { it[1] << 'failure1'; it[1] << 'failure2' }
        1 * state.setOutcome(!null as Throwable) >> {
            def failure = it[0]
            assert failure instanceof TaskValidationException
            assert failure.message == "Some problems were found with the configuration of $task."
            assert failure.causes[0] instanceof InvalidUserDataException
            assert failure.causes[0].message == 'failure1'
            assert failure.causes[1] instanceof InvalidUserDataException
            assert failure.causes[1].message == 'failure2'
        }
        0 * _
    }
}
