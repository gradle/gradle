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

package org.gradle.api.internal.tasks.execution

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository
import org.gradle.api.internal.changedetection.state.TaskCacheKeyCalculator
import org.gradle.api.internal.changedetection.state.TaskExecution
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.caching.BuildCacheKey
import spock.lang.Specification

class VerifyNoInputChangesTaskExecuterTest extends Specification {

    private TaskArtifactStateRepository repository = Mock()
    private TaskExecuter delegate = Mock()
    private TaskInternal task = Mock()
    private TaskArtifactState before = Mock()
    private TaskExecution beforeExecution = Mock()
    private String hashKeyBefore = "rsetnarosntanroston"
    private String hashKeyAfter = "345sratart22341234fw"
    private TaskArtifactState after = Mock()
    private TaskExecution afterExecution = Mock()
    private TaskStateInternal state = new TaskStateInternal("task")
    private TaskExecutionContext context = Mock()
    private TaskCacheKeyCalculator cacheKeyCalculator =  Mock()
    private VerifyNoInputChangesTaskExecuter executer = new VerifyNoInputChangesTaskExecuter(repository, delegate)

    def 'no exception if inputs do not change'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getTaskArtifactState() >> before
        1 * before.getCurrentExecution() >> beforeExecution
        1 * cacheKeyCalculator.calculate(beforeExecution, task) >> cacheKey(hashKeyBefore)
        then:
        1 * delegate.execute(task, state, context)

        then:
        1 * repository.getStateFor(task) >> after
        1 * after.getCurrentExecution() >> afterExecution
        1 * cacheKeyCalculator.calculate(afterExecution, task) >> cacheKey(hashKeyBefore)
        0 * _
    }

    def 'no cache key no comparison'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getTaskArtifactState() >> before
        1 * before.getCurrentExecution() >> beforeExecution
        1 * cacheKeyCalculator.calculate(beforeExecution, task) >> null
        then:
        1 * delegate.execute(task, state, context)
        0 * _
    }

    def 'exception if cache key changed'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getTaskArtifactState() >> before
        1 * before.getCurrentExecution() >> beforeExecution
        1 * cacheKeyCalculator.calculate(beforeExecution, task) >> cacheKey(hashKeyBefore)
        then:
        1 * delegate.execute(task, state, context)

        then:
        1 * repository.getStateFor(task) >> after
        1 * after.getCurrentExecution() >> afterExecution
        1 * cacheKeyCalculator.calculate(afterExecution, task) >> cacheKey(hashKeyAfter)
        0 * _

        TaskExecutionException e = thrown(TaskExecutionException)
        e.task == task
        e.cause.message == "The inputs for the task changed during the execution! Check if you have a `doFirst` changing the inputs."
    }

    private static BuildCacheKey cacheKey(String hash) {
        new BuildCacheKey() {
            @Override
            String getHashCode() {
                return hash
            }
        }
    }
}
