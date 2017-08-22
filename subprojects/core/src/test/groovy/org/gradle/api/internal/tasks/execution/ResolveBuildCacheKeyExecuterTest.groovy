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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.testing.internal.util.Specification
import org.gradle.util.Path

class ResolveBuildCacheKeyExecuterTest extends Specification {

    def taskState = Mock(TaskStateInternal)
    def task = Mock(TaskInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    def taskOutputs = Mock(TaskOutputsInternal)
    def delegate = Mock(TaskExecuter)
    def buildOperationExecutor = new TestBuildOperationExecutor()
    def executer = new ResolveBuildCacheKeyExecuter(delegate, buildOperationExecutor)
    def cacheKey = Mock(TaskOutputCachingBuildCacheKey)

    def "calculates build cache key"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        with(buildOpResult(), ResolveBuildCacheKeyExecuter.OperationResultImpl) {
            key == cacheKey
        }

        then:
        1 * task.getIdentityPath() >> Path.path(":foo")
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> cacheKey

        then:
        1 * task.getOutputs() >> taskOutputs
        1 * taskOutputs.getHasOutput() >> true
        1 * cacheKey.isValid() >> true
        1 * cacheKey.getHashCode() >> "0123456789abcdef"

        then:
        1 * taskContext.setBuildCacheKey(cacheKey)

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "propagates exceptions if cache key cannot be calculated"() {
        def failure = new RuntimeException("Bad cache key")

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getIdentityPath() >> Path.path(":foo")
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> {
            throw failure
        }
        0 * _

        def ex = thrown RuntimeException
        ex.is(failure)
        buildOpFailure().is(failure)
    }

    def "does not calculate cache key when task has no outputs"() {
        def noCacheKey = Mock(TaskOutputCachingBuildCacheKey)
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * task.getIdentityPath() >> Path.path(":foo")
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> noCacheKey

        then:
        1 * task.getOutputs() >> taskOutputs
        1 * taskOutputs.getHasOutput() >> false

        then:
        1 * taskContext.setBuildCacheKey(noCacheKey)

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _

        and:
        with(buildOpResult(), ResolveBuildCacheKeyExecuter.OperationResultImpl) {
            key == noCacheKey
        }
    }

    def "adapts key to result interface"() {
        given:
        def inputs = Mock(BuildCacheKeyInputs)
        def key = Mock(TaskOutputCachingBuildCacheKey) {
            getInputs() >> inputs
        }
        def adapter = new ResolveBuildCacheKeyExecuter.OperationResultImpl(key)

        when:
        inputs.inputHashes >> ImmutableSortedMap.copyOf(b: HashCode.fromString("bb"), a: HashCode.fromString("aa"))

        then:
        adapter.inputHashes == [a: "aa", b: "bb"]

        when:
        inputs.classLoaderHash >> HashCode.fromString("cc")

        then:
        adapter.classLoaderHash == "cc"

        when:
        inputs.actionClassLoaderHashes >> ImmutableList.copyOf([HashCode.fromString("ee"), HashCode.fromString("dd")])

        then:
        adapter.actionClassLoaderHashes == ["ee", "dd"]

        when:
        inputs.actionClassNames >> ImmutableList.copyOf(["foo", "bar"])

        then:
        adapter.actionClassNames == ["foo", "bar"]

        when:
        inputs.outputPropertyNames >> ImmutableSortedSet.copyOf(["2", "1"])

        then:
        adapter.outputPropertyNames == ["1", "2"]

        when:
        key.hashCode >> HashCode.fromString("ff")
        key.valid >> true

        then:
        adapter.buildCacheKey == "ff"
    }

    private SnapshotTaskInputsBuildOperationType.Result buildOpResult() {
        buildOperationExecutor.log.mostRecentResult(SnapshotTaskInputsBuildOperationType)
    }

    private Throwable buildOpFailure() {
        buildOperationExecutor.log.mostRecentFailure(SnapshotTaskInputsBuildOperationType)
    }

}
