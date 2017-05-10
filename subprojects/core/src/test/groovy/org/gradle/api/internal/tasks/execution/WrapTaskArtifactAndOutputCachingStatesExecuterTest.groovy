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

import com.google.common.collect.ImmutableSortedMap
import com.google.common.collect.ImmutableSortedSet
import com.google.common.hash.HashCode
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.ComputeTaskInputsHashesAndBuildCacheKeyDetails
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.internal.progress.TestBuildOperationExecutor
import spock.lang.Specification
import spock.lang.Subject

@Subject(WrapTaskArtifactAndOutputCachingStatesExecuter)
class WrapTaskArtifactAndOutputCachingStatesExecuterTest extends Specification {
    def delegate = Mock(TaskExecuter)
    def wrappedExecuter = Mock(TaskExecuter)
    def buildOpExecutor = new TestBuildOperationExecutor()
    def executer = new WrapTaskArtifactAndOutputCachingStatesExecuter(delegate, wrappedExecuter, buildOpExecutor)

    def 'build operation contains the expected result'() {
        given:
        def task = Mock(TaskInternal)
        def taskState = Mock(TaskStateInternal)
        def taskContext = Mock(TaskExecutionContext)
        def buildCacheKeyInputs = new BuildCacheKeyInputs(
            'foo',
            HashCode.fromLong(0),
            [],
            ImmutableSortedMap.of('input0', HashCode.fromLong(1)),
            ImmutableSortedSet.of('output')
        )
        def buildCacheKey = Mock(TaskOutputCachingBuildCacheKey)
        _ * buildCacheKey.isValid() >> true
        _ * buildCacheKey.getInputs() >> buildCacheKeyInputs

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * wrappedExecuter.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getBuildCacheKey() >> buildCacheKey

        then:
        1 * delegate.execute(task, taskState, taskContext)

        and:
        with(buildOpResult()) {
            valid
            inputs == buildCacheKeyInputs
        }
    }

    def "setting outcome stops delegation"() {
        given:
        def task = Mock(TaskInternal)
        def taskState = Mock(TaskStateInternal)
        def taskContext = Mock(TaskExecutionContext)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * wrappedExecuter.execute(task, taskState, taskContext)
        1 * taskState.getOutcome() >> TaskExecutionOutcome.EXECUTED

        then:
        0 * delegate.execute(task, taskState, taskContext)
    }

    private TaskOutputCachingBuildCacheKey buildOpResult() {
        buildOpExecutor.log.mostRecentResult(ComputeTaskInputsHashesAndBuildCacheKeyDetails)
    }
}
