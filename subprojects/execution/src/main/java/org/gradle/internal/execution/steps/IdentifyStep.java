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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.cache.Cache;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nonnull;

public class IdentifyStep<C extends ExecutionRequestContext, R extends Result> extends BuildOperationStep<C, R> implements DeferredExecutionAwareStep<C, R> {
    private final DeferredExecutionAwareStep<? super IdentityContext, R> delegate;

    public IdentifyStep(
        BuildOperationExecutor buildOperationExecutor,
        DeferredExecutionAwareStep<? super IdentityContext, R> delegate
    ) {
        super(buildOperationExecutor);
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return delegate.execute(work, createIdentityContext(work, context));
    }

    @Override
    public <T> Deferrable<Try<T>> executeDeferred(UnitOfWork work, C context, Cache<Identity, Try<T>> cache) {
        return delegate.executeDeferred(work, createIdentityContext(work, context), cache);
    }

    @Nonnull
    private IdentityContext createIdentityContext(UnitOfWork work, C context) {
        Class<? extends UnitOfWork> workType = work.getClass();
        return operation(operationContext -> {
                IdentityContext identityContext = createIdentityContextInternal(work, context);
                Identity identity = identityContext.getIdentity();
                operationContext.setResult(new Operation.Result() {
                    @Override
                    public Identity getIdentity() {
                        return identity;
                    }
                });
                return identityContext;
            },
            BuildOperationDescriptor
                .displayName("Identifying work")
                .details(new Operation.Details() {
                    @Override
                    public Class<?> getWorkType() {
                        return workType;
                    }
                })
        );
    }

    @Nonnull
    private IdentityContext createIdentityContextInternal(UnitOfWork work, C context) {
        InputFingerprinter.Result inputs = work.getInputFingerprinter().fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            work::visitIdentityInputs
        );

        ImmutableSortedMap<String, ValueSnapshot> identityInputProperties = inputs.getValueSnapshots();
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> identityInputFileProperties = inputs.getFileFingerprints();
        Identity identity = work.identify(identityInputProperties, identityInputFileProperties);

        return new IdentityContext(context, identityInputProperties, identityInputFileProperties, identity);
    }

    public interface Operation extends BuildOperationType<Operation.Details, Operation.Result> {
        interface Details {
            Class<?> getWorkType();
        }

        interface Result {
            Identity getIdentity();
        }
    }
}
