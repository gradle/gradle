/*
 * Copyright 2024 the original author or authors.
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

class ResolveNonIncrementalCachingStateStepTest extends AbstractResolveCachingStateStepTest<ValidationFinishedContext, ResolveNonIncrementalCachingStateStep<ValidationFinishedContext>> {
    @Override
    ResolveNonIncrementalCachingStateStep<ValidationFinishedContext> createStep() {
        return new ResolveNonIncrementalCachingStateStep<>(buildCache, false, delegate)
    }

    def "calculates cache key when execution state is available"() {
        delegateResult.executionReasons >> ImmutableList.of()
        delegateResult.reusedOutputOriginMetadata >> Optional.empty()
        delegateResult.afterExecutionOutputState >> Optional.empty()

        when:
        step.execute(work, context)
        then:
        _ * buildCache.enabled >> buildCacheEnabled
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)

        _ * context.validationProblems >> ImmutableList.of()
        1 * delegate.execute(work, { CachingContext context ->
            context.cachingState.cacheKeyCalculatedState.isPresent()
        }) >> delegateResult

        where:
        buildCacheEnabled << [true, false]
    }
}
