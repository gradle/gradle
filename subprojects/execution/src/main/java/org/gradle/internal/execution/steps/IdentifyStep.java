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
import org.gradle.internal.Try;
import org.gradle.internal.execution.DeferredExecutionHandler;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nonnull;
import java.util.Optional;

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.IDENTITY;

public class IdentifyStep<C extends ExecutionRequestContext, R extends Result> implements DeferredExecutionAwareStep<C, R> {
    private final DeferredExecutionAwareStep<? super IdentityContext, R> delegate;
    private final InputFingerprinter inputFingerprinter;

    public IdentifyStep(
        InputFingerprinter inputFingerprinter,
        DeferredExecutionAwareStep<? super IdentityContext, R> delegate
    ) {
        this.inputFingerprinter = inputFingerprinter;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return delegate.execute(work, createIdentityContext(work, context));
    }

    @Override
    public <T, O> T executeDeferred(UnitOfWork work, C context, Cache<Identity, Try<O>> cache, DeferredExecutionHandler<O, T> handler) {
        return delegate.executeDeferred(work, createIdentityContext(work, context), cache, handler);
    }

    @Nonnull
    private IdentityContext createIdentityContext(UnitOfWork work, C context) {
        InputFingerprinter.Result inputs = inputFingerprinter.fingerprintInputProperties(
            work,
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            (propertyName, type, identity) -> identity == IDENTITY);
        ImmutableSortedMap<String, ValueSnapshot> identityInputProperties = inputs.getValueSnapshots();
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> identityInputFileProperties = inputs.getFileFingerprints();

        Identity identity = work.identify(identityInputProperties, identityInputFileProperties);
        return new IdentityContext() {
            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }

            @Override
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return identityInputProperties;
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return identityInputFileProperties;
            }

            @Override
            public Identity getIdentity() {
                return identity;
            }
        };
    }
}
