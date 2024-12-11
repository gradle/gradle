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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.problems.Problem
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory
import org.gradle.internal.execution.history.BeforeExecutionState

import java.time.Duration

abstract class AbstractResolveCachingStateStepTest<C extends ValidationFinishedContext, S extends AbstractResolveCachingStateStep<C>> extends StepSpec<C> {

    def buildCache = Mock(BuildCacheController)
    S step
    def delegateResult = Stub(UpToDateResult)
    def beforeExecutionState = Stub(BeforeExecutionState) {
        inputFileProperties >> ImmutableSortedMap.of()
        inputProperties >> ImmutableSortedMap.of()
        outputFileLocationSnapshots >> ImmutableSortedMap.of()
    }

    abstract S createStep()

    def setup() {
        delegateResult.duration >> Duration.ofSeconds(1)
        step = createStep()
    }

    def "build cache disabled reason is reported when build cache is disabled"() {
        when:
        step.execute(work, context)
        then:
        _ * buildCache.enabled >> false
        _ * context.beforeExecutionState >> Optional.empty()
        _ * context.validationProblems >> ImmutableList.of()
        1 * delegate.execute(work, { CachingContext context ->
            context.cachingState.whenDisabled().map { it.disabledReasons*.category }.get() == [CachingDisabledReasonCategory.BUILD_CACHE_DISABLED]
            context.cachingState.whenDisabled().map { it.disabledReasons*.message }.get() == ["Build cache is disabled"]
        }) >> delegateResult
    }

    def "disables caching when work is invalid"() {
        when:
        step.execute(work, context)
        then:
        _ * buildCache.enabled >> false
        _ * context.beforeExecutionState >> Optional.empty()
        _ * context.validationProblems >> ImmutableList.of(Mock(Problem))
        1 * delegate.execute(work, { CachingContext context ->
            context.cachingState.whenDisabled().map { it.disabledReasons*.category }.get() == [CachingDisabledReasonCategory.VALIDATION_FAILURE]
            context.cachingState.whenDisabled().map { it.disabledReasons*.message }.get() == ["Caching has been disabled to ensure correctness. Please consult deprecation warnings for more details."]
        }) >> delegateResult
    }

    def "build cache disabled reason is determined without execution state"() {
        def disabledReason = new CachingDisabledReason(CachingDisabledReasonCategory.DISABLE_CONDITION_SATISFIED, "Something disabled")

        when:
        step.execute(work, context)
        then:
        _ * buildCache.enabled >> true
        _ * context.beforeExecutionState >> Optional.empty()
        _ * context.validationProblems >> ImmutableList.of()
        _ * work.shouldDisableCaching(null) >> Optional.of(disabledReason)
        1 * delegate.execute(work, { CachingContext context ->
            context.cachingState.whenDisabled().map { it.disabledReasons }.get() as List == [disabledReason]
        }) >> delegateResult
    }
}
