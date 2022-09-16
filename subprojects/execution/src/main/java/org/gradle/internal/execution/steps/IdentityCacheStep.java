/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cache.Cache;
import org.gradle.internal.Cast;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;

public class IdentityCacheStep<C extends IdentityContext> implements CachingResult.DeferredExecutionAwareStep<C> {

    private final CachingResult.Step<? super IdentityContext> delegate;

    public IdentityCacheStep(CachingResult.Step<? super IdentityContext> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> CachingResult<T> execute(UnitOfWork<T> work, C context) {
        return delegate.execute(work, context);
    }

    @Override
    public <T> Deferrable<Try<T>> executeDeferred(UnitOfWork<T> work, C context, Cache<Identity, Try<T>> cache) {
        Identity identity = context.getIdentity();
        Try<T> cachedOutput = cache.getIfPresent(identity);
        if (cachedOutput != null) {
            return Deferrable.completed(cachedOutput);
        } else {
            return Deferrable.deferred(() -> cache.get(
                identity,
                () -> execute(work, context).getExecution()
                    .map(Execution::getOutput)
                    .map(Cast::<T>uncheckedNonnullCast)));
        }
    }
}
