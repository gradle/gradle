/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.tasks.DefaultTaskOutputCachingBuildCacheKeyBuilder
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.caching.internal.tasks.TaskOutputCachingListener
import spock.lang.Specification

class ResolveBuildCacheKeyExecuterTest extends Specification {
    def taskState = Mock(TaskStateInternal)
    def task = Mock(TaskInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    def taskOutputs = Mock(TaskOutputsInternal)
    def delegate = Mock(TaskExecuter)
    def listener = Mock(TaskOutputCachingListener)
    def executer = new ResolveBuildCacheKeyExecuter(listener, delegate)
    def cacheKey = Mock(TaskOutputCachingBuildCacheKey)

    def "notifies listener after calculating cache key"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey

        then:
        1 * taskContext.setBuildCacheKey(cacheKey)

        then:
        1 * task.getOutputs() >> taskOutputs
        1 * taskOutputs.getHasOutput() >> true
        1 * listener.cacheKeyEvaluated(task, cacheKey)
        1 * cacheKey.isValid() >> true
        1 * cacheKey.getHashCode() >> "0123456789abcdef"

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "propagates exceptions if cache key cannot be calculated"() {
        def failure = new RuntimeException("Bad cache key")

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> {
            throw failure
        }
        0 * _

        def ex = thrown RuntimeException
        ex.is(failure)
    }

    def "does not call listener if task has no outputs"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> DefaultTaskOutputCachingBuildCacheKeyBuilder.NO_CACHE_KEY

        then:
        1 * taskContext.setBuildCacheKey(DefaultTaskOutputCachingBuildCacheKeyBuilder.NO_CACHE_KEY)

        then:
        1 * task.getOutputs() >> taskOutputs
        1 * taskOutputs.getHasOutput() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }
}
