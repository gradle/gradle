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
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.hash.HashCode;

import java.util.Optional;

public class ResolveIncrementalCachingStateStep<C extends IncrementalChangesContext> extends AbstractResolveCachingStateStep<C> {
    private final Step<? super IncrementalCachingContext, ? extends UpToDateResult> delegate;

    public ResolveIncrementalCachingStateStep(
        BuildCacheController buildCache,
        boolean emitDebugLogging,
        Step<? super IncrementalCachingContext, ? extends UpToDateResult> delegate
    ) {
        super(buildCache, emitDebugLogging);
        this.delegate = delegate;
    }

    @Override
    protected Optional<HashCode> getPreviousCacheKeyIfApplicable(C context) {
        return context.getChanges()
            .flatMap(changes -> context.getPreviousExecutionState()
                .filter(__ -> changes.getChangeDescriptions().isEmpty())
                .map(PreviousExecutionState::getCacheKey));
    }

    @Override
    protected UpToDateResult executeDelegate(UnitOfWork work, C context, CachingState cachingState) {
        return delegate.execute(work, new IncrementalCachingContext(context, cachingState));
    }
}
