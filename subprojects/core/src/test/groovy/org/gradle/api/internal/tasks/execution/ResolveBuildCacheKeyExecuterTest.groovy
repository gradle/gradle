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
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecuterResult
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.testing.internal.util.Specification

class ResolveBuildCacheKeyExecuterTest extends Specification {

    def taskState = Mock(TaskStateInternal)
    def task = Mock(TaskInternal)
    def taskContext = Mock(TaskExecutionContext)
    def beforeExecution = Mock(BeforeExecutionState)
    def taskProperties = Mock(TaskProperties)
    def delegate = Mock(TaskExecuter)
    def calculator = Mock(TaskCacheKeyCalculator)
    def executer = new ResolveBuildCacheKeyExecuter(calculator, false, delegate)
    def cacheKey = Mock(TaskOutputCachingBuildCacheKey)

    def "calculates build cache key"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        _ * taskContext.getBeforeExecutionState() >> Optional.of(beforeExecution)
        1 * calculator.calculate(task, beforeExecution, taskProperties, false) >> cacheKey

        then:
        1 * taskProperties.hasDeclaredOutputs() >> true
        1 * cacheKey.isValid() >> true
        1 * cacheKey.getHashCode() >> "0123456789abcdef"

        then:
        1 * taskContext.setBuildCacheKey(cacheKey)

        then:
        1 * delegate.execute(task, taskState, taskContext) >> TaskExecuterResult.WITHOUT_OUTPUTS
        0 * _
    }

    def "propagates exceptions if cache key cannot be calculated"() {
        def failure = new RuntimeException("Bad cache key")

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        _ * taskContext.getBeforeExecutionState() >> Optional.of(beforeExecution)
        1 * calculator.calculate(task, beforeExecution, taskProperties, false) >> {
            throw failure
        }
        0 * _

        def ex = thrown RuntimeException
        ex.is(failure)
    }

    def "does not calculate cache key when task has no outputs"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        _ * taskContext.getBeforeExecutionState() >> Optional.empty()
        0 * calculator.calculate(_ as TaskInternal, _ as BeforeExecutionState, _ as TaskProperties, _ as boolean)

        then:
        1 * taskContext.setBuildCacheKey({ TaskOutputCachingBuildCacheKey key -> !key.valid } as TaskOutputCachingBuildCacheKey)

        then:
        1 * delegate.execute(task, taskState, taskContext) >> TaskExecuterResult.WITHOUT_OUTPUTS
        0 * _
    }

    def "adapts key to result interface"() {
        given:
        def inputs = Mock(BuildCacheKeyInputs)
        def key = Mock(TaskOutputCachingBuildCacheKey) {
            getInputs() >> inputs
        }
        def adapter = new SnapshotTaskInputsMeasuringTaskExecuter.OperationResultImpl(key)

        when:
        inputs.inputValueHashes >> ImmutableSortedMap.copyOf(b: HashCode.fromInt(0x000000bb), a: HashCode.fromInt(0x000000aa))
        inputs.inputFiles >> ImmutableSortedMap.copyOf(c: { getHash: { HashCode.fromInt(0x000000cc) } } as CurrentFileCollectionFingerprint)

        then:
        adapter.inputValueHashesBytes.collectEntries { [(it.key):HashCode.fromBytes(it.value).toString()] } == [a: "000000aa", b: "000000bb"]

        when:
        inputs.nonCacheableInputProperties >> ImmutableSortedMap.of("bean", "Implementation loaded by unknown classloader.", "someOtherBean", "Implementation implemented by Java Lambda.")
        then:
        adapter.inputPropertiesLoadedByUnknownClassLoader == ["bean", "someOtherBean"] as SortedSet

        when:
        inputs.taskImplementation >> ImplementationSnapshot.of("org.gradle.TaskType", HashCode.fromInt(0x000000cc))
        then:
        HashCode.fromBytes(adapter.classLoaderHashBytes).toString() == "000000cc"

        when:
        inputs.actionImplementations >> ImmutableList.copyOf([ImplementationSnapshot.of("foo", HashCode.fromInt(0x000000ee)), ImplementationSnapshot.of("bar", HashCode.fromInt(0x000000dd))])
        then:
        adapter.actionClassLoaderHashesBytes.collect{ HashCode.fromBytes(it).toString() } == ["000000ee", "000000dd"]
        adapter.actionClassNames == ["foo", "bar"]

        when:
        inputs.outputPropertyNames >> ImmutableSortedSet.copyOf(["2", "1"])
        then:
        adapter.outputPropertyNames == ["1", "2"]

        when:
        key.hashCodeBytes >> HashCode.fromInt(0x000000ff).toByteArray()
        key.valid >> true
        then:
        HashCode.fromBytes(adapter.hashBytes).toString() == "000000ff"
    }
}
