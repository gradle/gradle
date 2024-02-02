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

import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.NoOpBuildCacheController;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.hash.HashCode;

import java.util.Optional;

public class ResolveNonIncrementalCachingStateStep<C extends ValidationFinishedContext> extends AbstractResolveCachingStateStep<C> {
    private final Step<? super NonIncrementalCachingContext, ? extends UpToDateResult> delegate;

    public ResolveNonIncrementalCachingStateStep(
        BuildCacheController buildCache,
        Step<? super NonIncrementalCachingContext, ? extends UpToDateResult> delegate
    ) {
        super(buildCache);
        this.delegate = delegate;
    }

    public ResolveNonIncrementalCachingStateStep(Step<? super NonIncrementalCachingContext, ? extends UpToDateResult> delegate) {
        this(NoOpBuildCacheController.INSTANCE, delegate);
    }

    @Override
    protected Optional<CacheKeyWithBeforeExecutionState> determineCacheKeyWithBeforeExecutionState(C context) {
        return context.getBeforeExecutionState()
            .map(beforeExecutionState -> new CacheKeyWithBeforeExecutionState() {
                @Override
                public Optional<HashCode> getCacheKey() {
                    return Optional.empty();
                }

                @Override
                public BeforeExecutionState getBeforeExecutionState() {
                    return beforeExecutionState;
                }
            });
    }

    @Override
    protected UpToDateResult executeDelegate(UnitOfWork work, C context, CachingState cachingState) {
        return delegate.execute(work, new NonIncrementalCachingContext(context, cachingState));
    }
}
