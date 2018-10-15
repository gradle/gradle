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
import org.gradle.api.internal.TaskOutputCachingState
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.tasks.DefaultTaskOutputs
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.caching.internal.tasks.DefaultTaskOutputCachingBuildCacheKeyBuilder
import spock.lang.Specification

class ResolveTaskOutputCachingStateExecuterTest extends Specification {

    def taskOutputCaching = Mock(TaskOutputCachingState)
    def outputs = Mock(TaskOutputsInternal)
    def task = Stub(TaskInternal) {
        getOutputs() >> outputs
    }
    def taskProperties = Mock(TaskProperties)
    def taskState = Mock(TaskStateInternal)
    def taskContext = Mock(TaskExecutionContext)
    def buildCacheKey = new DefaultTaskOutputCachingBuildCacheKeyBuilder().build()
    def delegate = Mock(TaskExecuter)
    def executer = new ResolveTaskOutputCachingStateExecuter(true, delegate)

    def "stores caching enabled in TaskState"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskContext.getBuildCacheKey() >> buildCacheKey
        1 * outputs.getCachingState(taskProperties, buildCacheKey) >> taskOutputCaching
        1 * taskState.setTaskOutputCaching(taskOutputCaching)
        1 * taskOutputCaching.isEnabled() >> true

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "stores caching disabled in TaskState"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskContext.getTaskProperties() >> taskProperties
        1 * taskContext.getBuildCacheKey() >> buildCacheKey
        1 * outputs.getCachingState(taskProperties, buildCacheKey) >> taskOutputCaching
        1 * taskState.setTaskOutputCaching(taskOutputCaching)
        1 * taskOutputCaching.isEnabled() >> false
        1 * taskOutputCaching.getDisabledReason() >> "Some"

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    def "when task output caching is disabled, state is DISABLED"() {
        executer = new ResolveTaskOutputCachingStateExecuter(false, delegate)
        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskState.setTaskOutputCaching(DefaultTaskOutputs.DISABLED)

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }
}
