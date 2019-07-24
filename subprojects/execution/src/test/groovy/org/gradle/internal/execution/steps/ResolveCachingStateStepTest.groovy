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

import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.internal.execution.BeforeExecutionContext
import org.gradle.internal.execution.CachingContext
import org.gradle.internal.execution.caching.CachingDisabledReason
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory

class ResolveCachingStateStepTest extends StepSpec<BeforeExecutionContext> {

    def buildCache = Mock(BuildCacheController)
    def step = new ResolveCachingStateStep(buildCache, true, delegate)

    @Override
    protected BeforeExecutionContext createContext() {
        Stub(BeforeExecutionContext)
    }

    def "build cache disabled reason is reported when build cache is disabled"() {
        when:
        step.execute(context)
        then:
        _ * buildCache.enabled >> false
        _ * context.beforeExecutionState >> Optional.empty()
        1 * delegate.execute(_) >> { CachingContext context ->
            assert context.cachingState.disabledReasons.get(0).category == CachingDisabledReasonCategory.BUILD_CACHE_DISABLED
        }
    }

    def "build cache disabled reason is determined without execution state"() {
        def disabledReason = new CachingDisabledReason(CachingDisabledReasonCategory.DISABLE_CONDITION_SATISFIED, "Something disabled")

        when:
        step.execute(context)
        then:
        _ * buildCache.enabled >> true
        _ * context.beforeExecutionState >> Optional.empty()
        _ * work.shouldDisableCaching(null) >> Optional.of(disabledReason)
        1 * delegate.execute(_) >> { CachingContext context ->
            assert context.cachingState.disabledReasons.get(0) == disabledReason
        }
    }
}
