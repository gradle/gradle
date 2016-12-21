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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.BuildCache
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.BuildCacheConfigurationInternal
import org.gradle.caching.internal.tasks.TaskOutputPacker
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory
import spock.lang.Specification

class SkipCachedTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def task = Mock(TaskInternal)
    def project = Mock(Project)
    def projectDir = Mock(File)
    def outputs = Mock(TaskOutputsInternal)
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    def buildCache = Mock(BuildCache)
    def buildCacheConfiguration = Mock(BuildCacheConfigurationInternal)
    def taskOutputPacker = Mock(TaskOutputPacker)
    def cacheKey = Mock(BuildCacheKey)
    def taskOutputOriginFactory = Mock(TaskOutputOriginFactory)
    def internalTaskExecutionListener = Mock(TaskOutputsGenerationListener)

    def executer = new SkipCachedTaskExecuter(taskOutputOriginFactory, buildCacheConfiguration, taskOutputPacker, internalTaskExecutionListener, delegate)

    def "skip task when cached results exist"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey
        1 * buildCacheConfiguration.isPullAllowed() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheConfiguration.getCache() >> buildCache
        1 * buildCache.getDescription() >> "test"

        then:
        1 * buildCache.load(cacheKey, _) >> true
        1 * taskState.setOutcome(TaskExecutionOutcome.FROM_CACHE)
        1 * taskState.setCacheable(true)
        1 * internalTaskExecutionListener.beforeTaskOutputsGenerated()
        0 * _
    }

    def "executes task and stores result when no cached result is available"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey
        1 * buildCacheConfiguration.isPullAllowed() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheConfiguration.getCache() >> buildCache
        1 * buildCache.getDescription() >> "test"

        then:
        1 * buildCache.load(cacheKey, _) >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * buildCacheConfiguration.isPushAllowed() >> true
        1 * taskState.getFailure() >> null
        1 * taskState.setCacheable(true)

        then:
        1 * buildCache.store(cacheKey, _)
        0 * _
    }

    def "executes task and stores result when use of cached result is not allowed"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey
        1 * buildCacheConfiguration.isPullAllowed() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> false

        then:
        1 * taskState.setCacheable(true)
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * buildCacheConfiguration.isPushAllowed() >> true
        1 * taskState.getFailure() >> null

        then:
        1 * buildCacheConfiguration.getCache() >> buildCache
        1 * buildCache.getDescription() >> "test"

        then:
        1 * buildCache.store(cacheKey, _)
        0 * _
    }

    def "does not cache results when executed task fails"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey
        1 * buildCacheConfiguration.isPullAllowed() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheConfiguration.getCache() >> buildCache
        1 * buildCache.getDescription() >> "test"

        then:
        1 * buildCache.load(cacheKey, _) >> false

        then:
        1 * taskState.setCacheable(true)
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * buildCacheConfiguration.isPushAllowed() >> true
        1 * taskState.getFailure() >> new RuntimeException()
        0 * _
    }

    def "executes task and does not cache results when cacheIf is false"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.setCacheable(false)
        0 * _
    }

    def "executes task and does not cache results when task is not allowed to use cache"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> true
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.setCacheable(false)
        0 * _
    }

    def "executes task and does not cache results when task does not declare outputs"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> true
        1 * outputs.hasDeclaredOutputs() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.setCacheable(false)
        0 * _
    }

    def "fails when cacheIf() clause cannot be evaluated"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        def ex = thrown GradleException
        ex.message == "Could not evaluate TaskOutputs.cacheIf for ${task}." as String
        ex.cause instanceof RuntimeException
        ex.cause.message == "Bad cacheIf() clause"

        1 * task.getOutputs() >> outputs
        1 * outputs.isCacheEnabled() >> { throw new RuntimeException("Bad cacheIf() clause") }
    }

    def "fails if cache key cannot be calculated"() {
        when:
        executer.execute(task, taskState, taskContext)
        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        def ex = thrown GradleException
        ex.message == "Could not build cache key for ${task}." as String
        ex.cause instanceof RuntimeException
        ex.cause.message == "Bad cache key"
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> { throw new RuntimeException("Bad cache key") }
    }

    def "fails when cache backend throws fatal exception while finding result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey
        1 * buildCacheConfiguration.isPullAllowed() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheConfiguration.getCache() >> buildCache
        1 * buildCache.getDescription() >> "test"

        then:
        1 * buildCache.load(cacheKey, _) >> { throw new RuntimeException("unknown error") }

        then:
        1 * taskState.setCacheable(true)

        then:
        0 * _
        then:
        RuntimeException e = thrown()
        e.message == "unknown error"
    }

    def "fails when cache backend throws fatal exception while storing cached result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getOutputs() >> outputs
        1 * outputs.hasDeclaredOutputs() >> true
        1 * outputs.isCacheAllowed() >> true
        1 * outputs.isCacheEnabled() >> true

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey
        1 * buildCacheConfiguration.isPullAllowed() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheConfiguration.getCache() >> buildCache
        1 * buildCache.getDescription() >> "test"

        then:
        1 * buildCache.load(cacheKey, _) >> false

        then:
        1 * taskState.setCacheable(true)
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * buildCacheConfiguration.isPushAllowed() >> true
        1 * taskState.getFailure() >> null
        1 * buildCache.store(cacheKey, _) >> { throw new RuntimeException("unknown error") }
        0 * _
        then:
        RuntimeException e = thrown()
        e.message == "unknown error"
    }
}
