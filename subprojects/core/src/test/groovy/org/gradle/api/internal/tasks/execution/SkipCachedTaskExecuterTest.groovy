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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputCachingState
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.command.BuildCacheCommandFactory
import org.gradle.caching.internal.command.BuildCacheLoadListener
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.caching.internal.controller.BuildCacheLoadCommand
import org.gradle.caching.internal.controller.BuildCacheStoreCommand
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.caching.internal.packaging.CacheableTree
import org.gradle.caching.internal.packaging.UnrecoverableUnpackingException
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.internal.id.UniqueId
import org.gradle.testing.internal.util.Specification

class SkipCachedTaskExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def project = Mock(Project)
    def projectDir = Mock(File)
    def taskOutputCaching = Mock(TaskOutputCachingState)
    def localStateFiles = Stub(FileCollection)
    def taskProperties = Mock(TaskProperties)
    def task = Stub(TaskInternal)
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    def buildCacheController = Mock(BuildCacheController)
    def cacheKey = Mock(TaskOutputCachingBuildCacheKey)
    def taskOutputGenerationListener = Mock(TaskOutputChangesListener)
    def loadCommand = Mock(BuildCacheLoadCommand)
    def storeCommand = Mock(BuildCacheStoreCommand)
    def buildCacheCommandFactory = Mock(BuildCacheCommandFactory)
    def outputFingerprints = [:]

    def executer = new SkipCachedTaskExecuter(buildCacheController, taskOutputGenerationListener, buildCacheCommandFactory, delegate)

    def "skip task when cached results exist"() {
        def originId = UniqueId.generate()
        def originalExecutionTime = 1234L
        def metadata = new OriginMetadata(originId, originalExecutionTime)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.outputFileProperties >> ImmutableSortedSet.of()
        1 * taskProperties.localStateFiles >> localStateFiles
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, _ as SortedSet<CacheableTree>, task, localStateFiles, _ as BuildCacheLoadListener) >> loadCommand

        then:
        1 * buildCacheController.load(loadCommand) >> metadata

        then:
        1 * taskState.setOutcome(TaskExecutionOutcome.FROM_CACHE)
        1 * taskContext.setOriginMetadata(metadata)
        0 * _
    }

    def "executes task and stores result when no cached result is available"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.outputFileProperties >> ImmutableSortedSet.of()
        1 * taskProperties.localStateFiles >> localStateFiles
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(cacheKey, _ as SortedSet<CacheableTree>, task, localStateFiles, _ as BuildCacheLoadListener) >> loadCommand

        then:
        1 * buildCacheController.load(loadCommand) >> null

        then:
        1 * delegate.execute(task, taskState, taskContext)
        1 * taskState.getFailure() >> null

        then:
        1 * taskContext.getExecutionTime() >> 1
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.getOutputFingerprints() >> outputFingerprints
        1 * buildCacheCommandFactory.createStore(cacheKey, _ as SortedSet<CacheableTree>, outputFingerprints, task, 1) >> storeCommand

        then:
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    def "executes task and stores result when use of cached result is not allowed"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.getOutputFileProperties() >> ImmutableSortedSet.of()
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> false

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * taskState.getFailure() >> null

        then:
        1 * taskContext.getExecutionTime() >> 1
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.getOutputFingerprints() >> outputFingerprints
        1 * buildCacheCommandFactory.createStore(cacheKey, _ as SortedSet<CacheableTree>, outputFingerprints, task, 1) >> storeCommand

        then:
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    def "does not cache results when executed task fails"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.outputFileProperties >> ImmutableSortedSet.of()
        1 * taskProperties.localStateFiles >> localStateFiles
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_)

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * taskState.getFailure() >> new RuntimeException()
        0 * _
    }

    def "executes task and does not cache results when caching was disabled"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
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
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.outputFileProperties >> ImmutableSortedSet.of()
        1 * taskProperties.localStateFiles >> localStateFiles
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_) >> { throw new RuntimeException("unknown error") }

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * taskState.getFailure() >> null

        then:
        1 * taskContext.getExecutionTime() >> 1
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.getOutputFingerprints() >> outputFingerprints
        1 * buildCacheCommandFactory.createStore(cacheKey, _ as SortedSet<CacheableTree>, outputFingerprints, task, 1) >> storeCommand

        then:
        1 * buildCacheController.store(storeCommand)
        0 * _
    }

    def "fails when cache backend throws unrecoverable exception while finding result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.outputFileProperties >> ImmutableSortedSet.of()
        1 * taskProperties.localStateFiles >> localStateFiles
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_) >> { throw new UnrecoverableUnpackingException("unknown error") }

        then:
        0 * _
        then:
        def e = thrown UnrecoverableUnpackingException
        e.message == "unknown error"
    }

    def "does not fail when cache backend throws exception while storing cached result"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.taskProperties >> taskProperties
        1 * taskContext.buildCacheKey >> cacheKey
        interaction { cachingEnabled() }

        then:
        1 * taskProperties.outputFileProperties >> ImmutableSortedSet.of()
        1 * taskProperties.localStateFiles >> localStateFiles
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.isAllowedToUseCachedResults() >> true

        then:
        1 * buildCacheCommandFactory.createLoad(*_)
        1 * buildCacheController.load(_)

        then:
        1 * delegate.execute(task, taskState, taskContext)

        then:
        1 * taskState.getFailure() >> null

        then:
        1 * taskContext.getExecutionTime() >> 1
        1 * cacheKey.getDisplayName() >> "cache key"
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.getOutputFingerprints()
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
