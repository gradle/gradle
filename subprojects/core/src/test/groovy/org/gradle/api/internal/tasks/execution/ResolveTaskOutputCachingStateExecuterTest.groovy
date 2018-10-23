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
import org.gradle.api.internal.OverlappingOutputs
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputCachingState
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec
import org.gradle.api.internal.tasks.TaskExecuter
import org.gradle.api.internal.tasks.TaskExecutionContext
import org.gradle.api.internal.tasks.TaskOutputCachingDisabledReasonCategory
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.caching.internal.tasks.DefaultTaskOutputCachingBuildCacheKeyBuilder
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.impl.ImplementationSnapshot
import org.gradle.testing.internal.util.Specification

import javax.annotation.Nullable


class ResolveTaskOutputCachingStateExecuterTest extends Specification {

    def task = Stub(TaskInternal)
    def cacheableOutputProperty = Mock(CacheableTaskOutputFilePropertySpec)
    def cacheKey = Stub(TaskOutputCachingBuildCacheKey) {
        isValid() >> true
    }

    def "error message contains which cacheIf spec failed to evaluate"() {
        when:
        resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [spec({ throw new RuntimeException()}, "Exception is thrown")],
                [],
                null
        )

        then:
        GradleException e = thrown()
        e.message.contains("Could not evaluate spec for 'Exception is thrown'.")
    }

    def "error message contains which doNotCacheIf spec failed to evaluate"() {
        when:
        resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [spec({ true })],
                [spec({ "throw new RuntimeException()" }, "Exception is thrown")],
                null
        )

        then:
        GradleException e = thrown()
        e.message.contains("Could not evaluate spec for 'Exception is thrown'.")
    }

    def "report no reason if the task is cacheable"() {
        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [spec({ true })],
                [],
                null
        )

        then:
        state.enabled
        state.disabledReasonCategory == null
        state.disabledReason == null
    }

    def "caching is disabled with no outputs"() {
        when:
        def state = resolveCachingState(
                [],
                cacheKey,
                task,
                [spec({ true })],
                [],
                null
        )

        then:
        !state.enabled
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NO_OUTPUTS_DECLARED
        state.disabledReason == "No outputs declared"
    }

    def "no cacheIf() means no caching"() {
        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [],
                [],
                null
        )

        then:
        !state.enabled
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NOT_ENABLED_FOR_TASK
        state.disabledReason == "Caching has not been enabled for the task"
    }

    def "can turn caching off via cacheIf()"() {
        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [spec({ false }, "Cacheable test")],
                [],
                null
        )

        then:
        !state.enabled
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.CACHE_IF_SPEC_NOT_SATISFIED
        state.disabledReason == "'Cacheable test' not satisfied"
    }

    def "can turn caching off via doNotCacheIf()"() {
        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [spec({ true })],
                [spec({ true }, "Uncacheable test")],
                null
        )

        then:
        !state.enabled
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.DO_NOT_CACHE_IF_SPEC_SATISFIED
        state.disabledReason == "'Uncacheable test' satisfied"
    }

    def "caching is disabled for non-cacheable file outputs is reported"() {
        when:
        def state = resolveCachingState(
                [Stub(TaskOutputFilePropertySpec) {
                    getPropertyName() >> "non-cacheable property"
                }],
                cacheKey,
                task,
                [spec({ true })],
                [],
                null
        )
        then:
        !state.enabled
        state.disabledReason == "Output property 'non-cacheable property' contains a file tree"
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_TREE_OUTPUT
    }

    def "caching is disabled when cache key is invalid because of invalid task implementation"() {
        def builder = new DefaultTaskOutputCachingBuildCacheKeyBuilder()
        builder.appendTaskImplementation(ImplementationSnapshot.of("org.gradle.TaskType", null))
        def invalidBuildCacheKey = builder.build()

        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                invalidBuildCacheKey,
                task,
                [spec({ true })],
                [],
                null
        )

        then:
        !state.enabled
        state.disabledReason == "Task class was loaded with an unknown classloader (class 'org.gradle.TaskType')."
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_TASK_IMPLEMENTATION
    }

    def "caching is disabled when cache key is invalid because of invalid task action"() {
        def builder = new DefaultTaskOutputCachingBuildCacheKeyBuilder()
        builder.appendTaskActionImplementations([ImplementationSnapshot.of('org.my.package.MyPlugin$$Lambda$1/23246642345', HashCode.fromInt(12345))])
        def invalidBuildCacheKey = builder.build()

        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                invalidBuildCacheKey,
                task,
                [spec({ true })],
                [],
                null
        )

        then:
        !state.enabled
        state.disabledReason == 'Task action was implemented by the Java lambda \'org.my.package.MyPlugin$$Lambda$1/23246642345\'. Using Java lambdas is not supported, use an (anonymous) inner class instead.'
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_TASK_ACTION
    }

    def "caching is disabled when cache key is invalid because of invalid input"() {
        def builder = new DefaultTaskOutputCachingBuildCacheKeyBuilder()
        builder.inputPropertyNotCacheable("someProperty", 'was implemented by the Java lambda \'org.my.package.MyPlugin$$Lambda$5/342523421\'')
        def invalidBuildCacheKey = builder.build()

        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                invalidBuildCacheKey,
                task,
                [spec({ true })],
                [],
                null
        )

        then:
        !state.enabled
        state.disabledReason == 'Non-cacheable inputs: property \'someProperty\' was implemented by the Java lambda \'org.my.package.MyPlugin$$Lambda$5/342523421\''
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.NON_CACHEABLE_INPUTS
    }

    def "caching is disabled when cache key is invalid because of overlapping outputs"() {
        def overlappingOutputs = new OverlappingOutputs("someProperty", "path/to/outputFile")

        when:
        def state = resolveCachingState(
                [cacheableOutputProperty],
                cacheKey,
                task,
                [spec({ true })],
                [],
                overlappingOutputs
        )

        then:
        !state.enabled
        state.disabledReason == "Gradle does not know how file 'path/to/outputFile' was created (output property 'someProperty'). Task output caching requires exclusive access to output paths to guarantee correctness."
        state.disabledReasonCategory == TaskOutputCachingDisabledReasonCategory.OVERLAPPING_OUTPUTS
    }

    def "when build cache is disabled, state is DISABLED"() {
        def taskState = Mock(TaskStateInternal)
        def taskContext = Mock(TaskExecutionContext)
        def delegate = Mock(TaskExecuter)
        def executer = new ResolveTaskOutputCachingStateExecuter(false, delegate)

        when:
        executer.execute(task, taskState, taskContext)

        then:
        1 * taskState.setTaskOutputCaching({ !it.enabled } as TaskOutputCachingState)

        then:
        1 * delegate.execute(task, taskState, taskContext)
        0 * _
    }

    static def spec(Spec<TaskInternal> spec, String description = "test cacheIf()") {
        new SelfDescribingSpec<TaskInternal>(spec, description)
    }

    static TaskOutputCachingState resolveCachingState(
            Collection<TaskOutputFilePropertySpec> outputFileProperties,
            TaskOutputCachingBuildCacheKey buildCacheKey,
            TaskInternal task,
            Collection<SelfDescribingSpec<TaskInternal>> cacheIfSpecs,
            Collection<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs,
            @Nullable OverlappingOutputs overlappingOutputs
    ) {
        return ResolveTaskOutputCachingStateExecuter.resolveCachingState(
                !outputFileProperties.isEmpty(),
                outputFileProperties,
                buildCacheKey,
                task,
                cacheIfSpecs,
                doNotCacheIfSpecs,
                overlappingOutputs
        )
    }
}
