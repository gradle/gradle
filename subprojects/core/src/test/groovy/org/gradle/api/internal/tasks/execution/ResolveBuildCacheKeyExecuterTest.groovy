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

import org.gradle.api.GradleException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskStateInternal
import spock.lang.Specification

class ResolveBuildCacheKeyExecuterTest extends Specification {
    def taskState = Mock(TaskStateInternal)
    def task = Mock(TaskInternal)
    def taskContext = Mock(TaskExecutionContext)
    def taskArtifactState = Mock(TaskArtifactState)
    def delegate = Mock(TaskExecuter)
    def listener = Mock(TaskOutputCachingListener)
    def executer = new ResolveBuildCacheKeyExecuter(listener, delegate)

    def "fails if cache key cannot be calculated"() {
        when:
        executer.execute(task, taskState, taskContext)

        then:
        def ex = thrown GradleException
        ex.message == "Could not build cache key for ${task}." as String
        ex.cause instanceof RuntimeException
        ex.cause.message == "Bad cache key"
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> { throw new RuntimeException("Bad cache key") }
    }

    def "rethrows Gradle exception"() {
        def e = new GradleException("Original exception")

        when:
        executer.execute(task, taskState, taskContext)

        then:
        def ex = thrown GradleException
        ex.is e
        1 * taskContext.getTaskArtifactState() >> taskArtifactState
        1 * taskArtifactState.calculateCacheKey() >> { throw e }
    }

}
