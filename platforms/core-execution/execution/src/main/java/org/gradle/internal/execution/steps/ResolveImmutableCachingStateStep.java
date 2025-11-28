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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;

public class ResolveImmutableCachingStateStep<C extends ImmutableValidationFinishedContext> extends AbstractResolveCachingStateStep<C> {
    private final Step<? super ImmutableCachingContext, ? extends UpToDateResult> delegate;

    public ResolveImmutableCachingStateStep(
        BuildCacheController buildCache,
        boolean emitDebugLogging,
        Step<? super ImmutableCachingContext, ? extends UpToDateResult> delegate
    ) {
        super(buildCache, emitDebugLogging);
        this.delegate = delegate;
    }

    @Override
    protected void checkIfWorkIsCacheable(UnitOfWork work, C context, ImmutableList.Builder<CachingDisabledReason> cachingDisabledReasonsBuilder) {
        work.shouldDisableCaching(null)
            .ifPresent(cachingDisabledReasonsBuilder::add);
    }

    @Override
    protected UpToDateResult executeDelegate(UnitOfWork work, C context, CachingState cachingState) {
        return delegate.execute(work, new ImmutableCachingContext(context, cachingState));
    }
}
