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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.cache.Cache;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.DeferredResult;
import org.gradle.internal.execution.Identity;
import org.gradle.internal.execution.ImplementationVisitor;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentifyStep<C extends ExecutionRequestContext, R extends Result> extends BuildOperationStep<C, R> implements DeferredExecutionAwareStep<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentifyStep.class);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final DeferredExecutionAwareStep<? super IdentityContext, R> delegate;

    public IdentifyStep(
        BuildOperationRunner buildOperationRunner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        DeferredExecutionAwareStep<? super IdentityContext, R> delegate
    ) {
        super(buildOperationRunner);
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        return delegate.execute(work, createIdentityContext(work, context));
    }

    @Override
    public <T> Deferrable<Try<T>> executeDeferred(UnitOfWork work, C context, Cache<Identity, DeferredResult<T>> cache) {
        return delegate.executeDeferred(work, createIdentityContext(work, context), cache);
    }

    private IdentityContext createIdentityContext(UnitOfWork work, C context) {
        ImplementationsBuilder implementationsBuilder = new ImplementationsBuilder(classLoaderHierarchyHasher);
        work.visitImplementations(implementationsBuilder);
        ImplementationSnapshot implementation = implementationsBuilder.getImplementation();
        ImmutableList<ImplementationSnapshot> additionalImplementations = implementationsBuilder.getAdditionalImplementations();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", work.getDisplayName(), implementation);
            LOGGER.debug("Additional implementations for {}: {}", work.getDisplayName(), additionalImplementations);
        }

        InputFingerprinter.Result inputs = work.getInputFingerprinter().fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            work::visitImmutableInputs,
            work.getInputDependencyChecker(context.getValidationContext())
        );

        ImmutableSortedMap<String, ValueSnapshot> scalarInputProperties = inputs.getValueSnapshots();
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fileInputProperties = inputs.getFileFingerprints();
        Identity identity = work.identify(scalarInputProperties, fileInputProperties);

        return new IdentityContext(
            context,
            implementation,
            additionalImplementations,
            scalarInputProperties,
            fileInputProperties,
            identity);
    }

    private static class ImplementationsBuilder implements ImplementationVisitor {
        private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
        @Nullable
        private ImplementationSnapshot implementation;
        private final ImmutableList.Builder<ImplementationSnapshot> additionalImplementations = ImmutableList.builder();

        public ImplementationsBuilder(ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        }

        @Override
        public void visitImplementation(Class<?> implementation) {
            this.implementation = ImplementationSnapshot.of(implementation, classLoaderHierarchyHasher);
        }

        @Override
        public void visitAdditionalImplementation(ImplementationSnapshot implementation) {
            this.additionalImplementations.add(implementation);
        }

        public ImplementationSnapshot getImplementation() {
            if (implementation == null) {
                throw new IllegalStateException("No implementation is set");
            }
            return implementation;
        }

        public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
            return additionalImplementations.build();
        }
    }
}
