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
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.operations.execution.ExecuteDeferredWorkProgressDetails;

import javax.annotation.Nullable;
import java.util.Optional;

public class IdentityCacheStep<C extends IdentityContext, R extends WorkspaceResult> implements DeferredExecutionAwareStep<C, R> {

    private final BuildOperationProgressEventEmitter progressEventEmitter;
    private final Step<? super IdentityContext, ? extends R> delegate;

    public IdentityCacheStep(BuildOperationProgressEventEmitter progressEventEmitter, Step<? super IdentityContext, ? extends R> delegate) {
        this.progressEventEmitter = progressEventEmitter;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return delegate.execute(work, context);
    }

    @Override
    public <T> Deferrable<Try<T>> executeDeferred(UnitOfWork work, C context, Cache<Identity, IdentityCacheResult<T>> cache) {
        Identity identity = context.getIdentity();
        IdentityCacheResult<T> cacheResult = cache.getIfPresent(identity);
        if (cacheResult != null) {
            emitExecuteDeferredProgressDetails(work, context.getIdentity(), cacheResult);
            return Deferrable.completed(cacheResult.getResult());
        } else {
            return Deferrable.deferred(() -> {
                IdentityCacheResult<T> maybeExecutedResult = cache.get(
                    identity,
                    () -> executeInCache(work, context)
                );
                emitExecuteDeferredProgressDetails(work, context.getIdentity(), maybeExecutedResult);
                return maybeExecutedResult.getResult();
            });
        }
    }

    // TODO: Move this logic in a step around IdentityCacheStep
    private <T> void emitExecuteDeferredProgressDetails(UnitOfWork work, Identity identity, IdentityCacheResult<T> cacheResult) {
        cacheResult.getOriginMetadata().ifPresent(originMetadata ->
            progressEventEmitter.emitNowIfCurrent(new DefaultExecuteDeferredWorkProgressDetails(
                work.getBuildOperationWorkType().orElse(null),
                identity,
                originMetadata
            ))
        );
    }

    private <T> IdentityCacheResult<T> executeInCache(UnitOfWork work, C context) {
        R result = execute(work, context);
        return new DefaultIdentityCacheResult<>(
            result
                .getOutputAs(Object.class)
                .map(Cast::<T>uncheckedNonnullCast),
            result
                .getReusedOutputOriginMetadata()
                .orElseGet(() -> result.getAfterExecutionOutputState()
                    .map(ExecutionOutputState::getOriginMetadata)
                    .orElse(null))
        );
    }

    private static class DefaultIdentityCacheResult<T> implements IdentityCacheResult<T> {
        private final Try<T> result;
        private final OriginMetadata originMetadata;

        public DefaultIdentityCacheResult(Try<T> result, @Nullable OriginMetadata originMetadata) {
            this.result = result;
            this.originMetadata = originMetadata;
        }

        @Override
        public Try<T> getResult() {
            return result;
        }

        @Override
        public Optional<OriginMetadata> getOriginMetadata() {
            return Optional.ofNullable(originMetadata);
        }
    }

    private static class DefaultExecuteDeferredWorkProgressDetails implements ExecuteDeferredWorkProgressDetails {
        private final String workType;
        private final Identity identity;
        private final OriginMetadata originMetadata;

        public DefaultExecuteDeferredWorkProgressDetails(
            @Nullable String workType,
            Identity identity,
            OriginMetadata originMetadata
        ) {
            this.workType = workType;
            this.identity = identity;
            this.originMetadata = originMetadata;
        }

        @Nullable
        @Override
        public String getWorkType() {
            return workType;
        }

        @Override
        public String getIdentity() {
            return identity.getUniqueId();
        }

        @Override
        public String getOriginBuildInvocationId() {
            return originMetadata.getBuildInvocationId();
        }

        @Override
        public byte[] getOriginBuildCacheKeyBytes() {
            return originMetadata.getBuildCacheKey().toByteArray();
        }

        @Override
        public long getOriginExecutionTime() {
            return originMetadata.getExecutionTime().toMillis();
        }
    }
}
