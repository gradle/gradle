/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.GradleException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.properties.CacheableOutputFilePropertySpec
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec
import org.gradle.api.internal.tasks.properties.OutputFilePropertySpec
import org.gradle.api.internal.tasks.properties.TaskProperties
import org.gradle.api.specs.Spec
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.history.OverlappingOutputs
import org.gradle.internal.file.RelativeFilePathResolver
import spock.lang.Specification

import javax.annotation.Nullable

class DefaultTaskCacheabilityResolverTest extends Specification {
    def task = Stub(TaskInternal)
    def inputProperty = Stub(InputFilePropertySpec)
    def cacheableOutputProperty = Stub(CacheableOutputFilePropertySpec)
    def relativeFilePathResolver = Mock(RelativeFilePathResolver)
    def resolver = new DefaultTaskCacheabilityResolver(relativeFilePathResolver)

    def "report no reason if the task is cacheable"() {
        expect:
        determineNoCacheReason(
            [inputProperty],
            [cacheableOutputProperty],
            [spec({ true })],
        ) == null
    }

    def "caching is disabled with no outputs"() {
        when:
        def reason = determineNoCacheReason(
            [],
            [],
            [spec({ true })],
        )

        then:
        reason.category == CachingDisabledReasonCategory.NO_OUTPUTS_DECLARED
        reason.message == "No outputs declared"
    }

    def "no cacheIf() means no caching"() {
        when:
        def reason = determineNoCacheReason(
            [],
            [cacheableOutputProperty]
        )

        then:
        reason.category == CachingDisabledReasonCategory.NOT_CACHEABLE
        reason.message == "Caching has not been enabled for the task"
    }

    def "can turn caching off via cacheIf()"() {
        when:
        def reason = determineNoCacheReason(
            [],
            [cacheableOutputProperty],
            [spec({ false }, "Cacheable test")]
        )

        then:
        reason.category == CachingDisabledReasonCategory.ENABLE_CONDITION_NOT_SATISFIED
        reason.message == "'Cacheable test' not satisfied"
    }

    def "error message contains which cacheIf spec failed to evaluate"() {
        when:
        determineNoCacheReason(
            [],
            [cacheableOutputProperty],
            [spec({ throw new RuntimeException() }, "Exception is thrown")],
        )

        then:
        def ex = thrown GradleException
        ex.message == "Could not evaluate spec for 'Exception is thrown'."
    }

    def "can turn caching off via doNotCacheIf()"() {
        when:
        def reason = determineNoCacheReason(
            [],
            [cacheableOutputProperty],
            [spec({ true })],
            [spec({ true }, "Uncacheable test")]
        )

        then:
        reason.category == CachingDisabledReasonCategory.DISABLE_CONDITION_SATISFIED
        reason.message == "'Uncacheable test' satisfied"
    }

    def "error message contains which doNotCacheIf spec failed to evaluate"() {
        when:
        determineNoCacheReason(
            [],
            [cacheableOutputProperty],
            [spec({ true })],
            [spec({ "throw new RuntimeException()" }, "Exception is thrown")]
        )

        then:
        def ex = thrown GradleException
        ex.message == "Could not evaluate spec for 'Exception is thrown'."
    }

    def "caching is disabled for non-cacheable file outputs is reported"() {
        when:
        def reason = determineNoCacheReason(
            [],
            [Stub(OutputFilePropertySpec) {
                getPropertyName() >> "non-cacheable property"
            }],
            [spec({ true })]
        )

        then:
        reason.category == CachingDisabledReasonCategory.NON_CACHEABLE_OUTPUT
        reason.message == "Output property 'non-cacheable property' contains a file tree"
    }

    def "caching is disabled when cache key is invalid because of overlapping outputs"() {
        def overlappingOutputs = new OverlappingOutputs("someProperty", "path/to/outputFile")

        when:
        def reason = determineNoCacheReason(
            [],
            [cacheableOutputProperty],
            [spec({ true })],
            [],
            overlappingOutputs
        )

        then:
        reason.category == CachingDisabledReasonCategory.OVERLAPPING_OUTPUTS
        reason.message == "Gradle does not know how file 'relative/path' was created (output property 'someProperty'). Task output caching requires exclusive access to output paths to guarantee correctness (i.e. multiple tasks are not allowed to produce output in the same location)."

        1 * relativeFilePathResolver.resolveForDisplay(overlappingOutputs.overlappedFilePath) >> "relative/path"
    }

    def "caching is disabled for untracked tasks"() {
        when:
        def reason = determineNoCacheReason(
            [inputProperty],
            [cacheableOutputProperty],
            [spec({ true })]
        )

        then:
        task.getReasonNotToTrackState() >> Optional.of("For tests")

        reason.category == CachingDisabledReasonCategory.DISABLE_CONDITION_SATISFIED
        reason.message == "Task is untracked because: For tests"
    }

    static def spec(Spec<TaskInternal> spec, String description = "test cacheIf()") {
        new SelfDescribingSpec<TaskInternal>(spec, description)
    }

    @Nullable
    CachingDisabledReason determineNoCacheReason(
        Collection<InputFilePropertySpec> inputFileProperties,
        Collection<OutputFilePropertySpec> outputFileProperties,
        Collection<SelfDescribingSpec<TaskInternal>> cacheIfSpecs = [],
        Collection<SelfDescribingSpec<TaskInternal>> doNotCacheIfSpecs = [],
        @Nullable OverlappingOutputs overlappingOutputs = null
    ) {
        def taskProperties = Stub(TaskProperties) {
            getInputFileProperties() >> ImmutableSortedSet.copyOf(inputFileProperties)
            hasDeclaredOutputs() >> !outputFileProperties.isEmpty()
            getOutputFileProperties() >> ImmutableSortedSet.copyOf(outputFileProperties)
        }
        resolver.shouldDisableCaching(
            task,
            taskProperties,
            cacheIfSpecs,
            doNotCacheIfSpecs,
            overlappingOutputs
        ).orElse(null)
    }
}
