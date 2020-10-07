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

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.Try;
import org.gradle.internal.execution.DeferredExecutionAwareStep;
import org.gradle.internal.execution.DeferredResultProcessor;
import org.gradle.internal.execution.ExecutionRequestContext;
import org.gradle.internal.execution.IdentityContext;
import org.gradle.internal.execution.Result;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nonnull;
import java.util.Optional;

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.IDENTITY;
import static org.gradle.internal.execution.impl.InputFingerprintUtil.fingerprintInputFiles;
import static org.gradle.internal.execution.impl.InputFingerprintUtil.fingerprintInputProperties;

public class IdentifyStep<C extends ExecutionRequestContext, R extends Result> implements DeferredExecutionAwareStep<C, R> {
    private final DeferredExecutionAwareStep<? super IdentityContext, R> delegate;
    private final ValueSnapshotter valueSnapshotter;

    public IdentifyStep(
        ValueSnapshotter valueSnapshotter,
        DeferredExecutionAwareStep<? super IdentityContext, R> delegate
    ) {
        this.valueSnapshotter = valueSnapshotter;
        this.delegate = delegate;
    }

    @Override
    public R execute(C context) {
        return delegate.execute(createIdentityContext(context));
    }

    @Override
    public <T, O> T executeDeferred(C context, Cache<Identity, Try<O>> cache, DeferredResultProcessor<O, T> processor) {
        return delegate.executeDeferred(createIdentityContext(context), cache, processor);
    }

    @Nonnull
    private IdentityContext createIdentityContext(C context) {
        ImmutableSortedMap<String, ValueSnapshot> identityInputProperties = fingerprintInputProperties(
            context.getWork(),
            ImmutableSortedMap.of(),
            valueSnapshotter,
            ImmutableSortedMap.of(),
            (propertyName, identity) -> identity == IDENTITY);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> identityInputFileProperties = fingerprintInputFiles(
            context.getWork(),
            ImmutableSortedMap.of(),
            (propertyName, type, identity) -> identity == IDENTITY);
        Identity identity = context.getWork().identify(identityInputProperties, identityInputFileProperties);
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

            @Override
            public UnitOfWork getWork() {
                return context.getWork();
            }
        };
    }
}
