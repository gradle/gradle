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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Cast;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.IdentityCacheResult;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.history.ExecutionOutputState;

import javax.annotation.Nullable;
import java.util.Optional;

public class IdentityCacheStep<C extends IdentityContext, R extends WorkspaceResult> implements DeferredExecutionAwareStep<C, R> {

    private final Step<? super IdentityContext, ? extends R> delegate;

    public IdentityCacheStep(Step<? super IdentityContext, ? extends R> delegate) {
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return delegate.execute(work, context);
    }

    @Override
    public <T> Deferrable<IdentityCacheResult<T>> executeDeferred(UnitOfWork work, C context, Cache<Identity, IdentityCacheResult<T>> cache) {
        Identity identity = context.getIdentity();
        IdentityCacheResult<T> cachedOutput = cache.getIfPresent(identity);
        if (cachedOutput != null) {
            return Deferrable.completed(cachedOutput);
        } else {
            return Deferrable.deferred(() -> cache.get(
                identity,
                () -> executeInCache(work, context)));
        }
    }

    private <T> IdentityCacheResult<T> executeInCache(UnitOfWork work, C context) {
        R result = execute(work, context);
        return new DefaultIdentityCacheResult<>(
            result
                .getOutputAs(Object.class)
                .map(Cast::<T>uncheckedNonnullCast),
            context.getIdentity(),
            result
                .getReusedOutputOriginMetadata()
                .orElseGet(() -> result.getAfterExecutionOutputState()
                    .map(ExecutionOutputState::getOriginMetadata)
                    .orElse(null))
        );
    }

    private static class DefaultIdentityCacheResult<T> implements IdentityCacheResult<T> {
        private final Try<T> result;
        private final Identity identity;
        private final OriginMetadata originMetadata;

        public DefaultIdentityCacheResult(Try<T> result, Identity identity, @Nullable OriginMetadata originMetadata) {
            this.result = result;
            this.identity = identity;
            this.originMetadata = originMetadata;
        }

        @Override
        public Try<T> getResult() {
            return result;
        }

        @Override
        public Identity getIdentity() {
            return identity;
        }

        @Override
        public Optional<OriginMetadata> getOriginMetadata() {
            return Optional.ofNullable(originMetadata);
        }
    }
}
