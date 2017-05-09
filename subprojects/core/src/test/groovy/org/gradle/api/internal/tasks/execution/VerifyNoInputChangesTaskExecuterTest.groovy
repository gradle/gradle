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
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs
import org.gradle.caching.internal.tasks.DefaultTaskOutputCachingBuildCacheKeyBuilder
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import spock.lang.Specification

class VerifyNoInputChangesTaskExecuterTest extends Specification {

    private TaskArtifactStateRepository repository = Mock()
    private TaskExecuter delegate = Mock()
    private TaskInternal task = Mock()
    private String hashKeyBefore = "rsetnarosntanroston"
    private String hashKeyAfter = "345sratart22341234fw"
    private TaskArtifactState after = Mock()
    private TaskStateInternal state = new TaskStateInternal()
    private TaskExecutionContext context = Mock()
    private VerifyNoInputChangesTaskExecuter executer = new VerifyNoInputChangesTaskExecuter(repository, delegate)

    def 'no exception if inputs do not change'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getBuildCacheKey() >> cacheKey(hashKeyBefore)
        then:
        1 * delegate.execute(task, state, context)

        then:
        1 * repository.getStateFor(task) >> after
        1 * after.calculateCacheKey() >> cacheKey(hashKeyBefore)
        0 * _
    }

    def 'no cache key no comparison'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getBuildCacheKey() >> invalidCacheKey()
        then:
        1 * delegate.execute(task, state, context)
        0 * _
    }

    def 'exception if cache key changed'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getBuildCacheKey() >> cacheKey(hashKeyBefore)
        then:
        1 * delegate.execute(task, state, context)

        then:
        1 * repository.getStateFor(task) >> after
        1 * after.calculateCacheKey() >> cacheKey(hashKeyAfter)
        0 * _

        TaskExecutionException e = thrown(TaskExecutionException)
        e.task == task
        e.cause.message == "The inputs for the task changed during the execution! Check if you have a `doFirst` changing the inputs."
    }

    def 'exception if cache key became invalid'() {
        when:
        executer.execute(task, state, context)

        then:
        1 * context.getBuildCacheKey() >> cacheKey(hashKeyBefore)
        then:
        1 * delegate.execute(task, state, context)

        then:
        1 * repository.getStateFor(task) >> after
        1 * after.calculateCacheKey() >> invalidCacheKey()
        0 * _

        TaskExecutionException e = thrown(TaskExecutionException)
        e.task == task
        e.cause.message == "The build cache key became invalid after the task has been executed!"
    }

    private static TaskOutputCachingBuildCacheKey cacheKey(String hash) {
        new TaskOutputCachingBuildCacheKey() {
            @Override
            String getHashCode() {
                return hash
            }

            @Override
            BuildCacheKeyInputs getInputs() {
                return null
            }

            @Override
            boolean isValid() {
                return true
            }
        }
    }

    private static TaskOutputCachingBuildCacheKey invalidCacheKey() {
        return new DefaultTaskOutputCachingBuildCacheKeyBuilder().build()
    }
}
