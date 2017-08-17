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

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.Project
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputCachingState
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.BuildCacheLoadCommand
import org.gradle.caching.internal.controller.BuildCacheStoreCommand
import org.gradle.caching.internal.tasks.TaskOutputCacheCommandFactory
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.caching.internal.tasks.UnrecoverableTaskOutputUnpackingException
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata
import org.gradle.internal.id.UniqueId
import spock.lang.Specification

class SkipCachedTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def project = Mock(Project)
    def projectDir = Mock(File)
    def taskOutputCaching = Mock(TaskOutputCachingState)
    def outputs = Mock(TaskOutputsInternal)
    def task = Stub(TaskInternal) {
        getOutputs() >> outputs
    }
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    def buildCacheController = Mock(BuildCacheController)
    def cacheKey = Mock(TaskOutputCachingBuildCacheKey)
    def taskOutputGenerationListener = Mock(TaskOutputsGenerationListener)
    def loadCommand = Mock(BuildCacheLoadCommand)
    def storeCommand = Mock(BuildCacheStoreCommand)
    def buildCacheCommandFactory = Mock(TaskOutputCacheCommandFactory)

    def executer = new SkipCachedTaskExecuter(buildCacheController, taskOutputGenerationListener, buildCacheCommandFactory, delegate)

    def "skip task when cached results exist"() {
        def originId = UniqueId.generate()

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true
        1 * cacheKey.isValid() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, _, task, taskOutputGenerationListener, _, _) >> loadCommand

        then:
        1 * buildCacheController.load(loadCommand) >> new TaskOutputOriginMetadata(originId)

        then:
        1 * taskState.setOutcome(TaskExecutionOutcome.FROM_CACHE)
        1 * taskContext.setOriginBuildInvocationId(originId)
        1 * taskArtifactState.snapshotAfterTaskExecution(null)
        0 * _
    }

    def "executes task and stores result when no cached result is available"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true
        1 * cacheKey.isValid() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, _, task, taskOutputGenerationListener, _, _) >> loadCommand

        then:
        1 * buildCacheController.load(loadCommand) >> null

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.getFailure() >> null
        1 * cacheKey.isValid() >> true

        then:
        1 * buildCacheCommandFactory.createStore(cacheKey, _, task, _) >> storeCommand

        then:
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    def "executes task and stores result when use of cached result is not allowed"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> false
        1 * cacheKey.isValid() >> true

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * taskState.getFailure() >> null
        1 * cacheKey.isValid() >> true

        then:
        1 * buildCacheCommandFactory.createStore(cacheKey, _, task, _) >> storeCommand

        then:
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    def "does not cache results when executed task fails"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true
        1 * cacheKey.isValid() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_)

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * cacheKey.isValid() >> true
        1 * taskState.getFailure() >> new RuntimeException()
        0 * _
    }

    def "does not cache results when cache key is invalid"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        interaction { cachingEnabled() }
        1 * taskContext.buildCacheKey >> cacheKey

        then:
        1 * cacheKey.isValid() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * cacheKey.isValid() >> false
        0 * _
    }

    def "executes task and does not cache results when cacheIf is false"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingDisabled() }

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "stores result when cache backend throws recoverable exception while loading result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * cacheKey.isValid() >> true
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_) >> { throw new RuntimeException("unknown error") }

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * taskState.getFailure() >> null
        1 * cacheKey.isValid() >> true

        then:
        1 * buildCacheCommandFactory.createStore(cacheKey, _, task, _) >> storeCommand

        then:
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    def "fails when cache backend throws unrecoverable exception while finding result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * cacheKey.isValid() >> true
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_) >> { throw new UnrecoverableTaskOutputUnpackingException("unknown error") }

        then:
        0 * _
        then:
        def e = thrown UnrecoverableTaskOutputUnpackingException
        e.message == "unknown error"
    }

    def "does not fail when cache backend throws exception while storing cached result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * outputs.getFileProperties() >> ImmutableSortedSet.of()
        1 * cacheKey.isValid() >> true
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_)

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * cacheKey.isValid() >> true
        1 * cacheKey.getDisplayName() >> "cache key"
        1 * taskState.getFailure() >> null
        1 * buildCacheCommandFactory.createStore(*_)
        1 * buildCacheController.store(_) >> { throw new RuntimeException("unknown error") }

        then:
        0 * _
    }

    private void cachingEnabled() {
        1 * taskState.getTaskOutputCaching() >> taskOutputCaching
        1 * taskOutputCaching.isEnabled() >> true
    }

    private void cachingDisabled() {
        1 * taskState.getTaskOutputCaching() >> taskOutputCaching
        1 * taskOutputCaching.isEnabled() >> false
    }
}
