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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.groovy.scripts.ScriptSource
import spock.lang.Specification

class SkipOnlyIfTaskExecuterTest extends Specification {
    private final TaskInternal task = Mock(TaskInternal)
    private final DescribingAndSpec<Task> spec = Mock(DescribingAndSpec)
    private final TaskStateInternal state = Mock(TaskStateInternal)
    private final TaskExecutionContext executionContext = Mock(TaskExecutionContext)

    private final ScriptSource scriptSource = Mock(ScriptSource)
    private final TaskExecuter delegate = Mock(TaskExecuter)
    private final SkipOnlyIfTaskExecuter executer = new SkipOnlyIfTaskExecuter(delegate)

    def setup() {
        ProjectInternal project = Mock(ProjectInternal)

        task.getProject() >> project
        project.getBuildScriptSource() >> scriptSource
        task.getOnlyIf() >> spec
    }

    void noMoreInteractions() {
        interaction {
            0 * task._
            0 * spec._
            0 * state._
            0 * executionContext._
            0 * scriptSource._
            0 * delegate._
        }
    }

    def executesTask() {
        when:
        executer.execute(task, state, executionContext)

        then:
        1 * spec.findUnsatisfiedSpec(task) >> null
        1 * delegate.execute(task, state, executionContext) >> TaskExecuterResult.WITHOUT_OUTPUTS
        noMoreInteractions()
    }

    def skipsTaskWhoseOnlyIfPredicateIsFalse() {
        when:
        executer.execute(task, state, executionContext)

        then:
        1 * spec.findUnsatisfiedSpec(task) >> Mock(SelfDescribingSpec)
        1 * state.setOutcome(TaskExecutionOutcome.SKIPPED)
        noMoreInteractions()
    }

    def handlesOldStyleOnlyIfSpec() {
        given:
        def project = task.project
        def otherTask = Mock(TaskInternal)
        Spec<Task> oldStyleSpec = Mock(Spec)
        otherTask.getProject() >> project
        otherTask.getOnlyIf() >> oldStyleSpec

        when:
        executer.execute(otherTask, state, executionContext)

        then:
        1 * oldStyleSpec.isSatisfiedBy(task) >> false
        1 * state.setOutcome(TaskExecutionOutcome.SKIPPED)
    }

    def wrapsOnlyIfPredicateFailure() {
        given:
        RuntimeException failure = new RuntimeException()
        GradleException thrownException = null

        when:
        executer.execute(task, state, executionContext)

        then:
        1 * spec.findUnsatisfiedSpec(task) >> { throw failure }
        1 * state.setOutcome(_ as Throwable) >> { args -> thrownException = args[0] }
        noMoreInteractions()

        thrownException.message.startsWith('Could not evaluate onlyIf predicate for')
        thrownException.cause.is(failure)
    }
}
