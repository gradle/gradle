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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Predicate;

public abstract class AbstractSkipEmptyWorkStep<C extends IdentityContext> implements Step<C, CachingResult> {
    private final WorkInputListeners workInputListeners;
    protected final Step<? super C, ? extends CachingResult> delegate;

    protected AbstractSkipEmptyWorkStep(
        WorkInputListeners workInputListeners,
        Step<? super C, ? extends CachingResult> delegate
    ) {
        this.workInputListeners = workInputListeners;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, C context) {
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFileFingerprints = context.getInputFileProperties();
        ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots = context.getInputProperties();
        InputFingerprinter.Result newInputs = fingerprintPrimaryInputs(work, context, knownFileFingerprints, knownValueSnapshots);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties = newInputs.getFileFingerprints();
        if (sourceFileProperties.isEmpty()) {
            return executeWithNonEmptySources(work, context);
        } else {
            if (hasEmptySources(sourceFileProperties, newInputs.getPropertiesRequiringIsEmptyCheck(), work)) {
                return skipExecutionWithEmptySources(work, context);
            } else {
                return executeWithNonEmptySources(work, recreateContextWithNewInputFiles(context, newInputs.getAllFileFingerprints()));
            }
        }
    }

    protected abstract C recreateContextWithNewInputFiles(C context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles);

    private static boolean hasEmptySources(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, ImmutableSet<String> propertiesRequiringIsEmptyCheck, UnitOfWork work) {
        if (propertiesRequiringIsEmptyCheck.isEmpty()) {
            return sourceFileProperties.values().stream()
                .allMatch(CurrentFileCollectionFingerprint::isEmpty);
        } else {
            // We need to check the underlying file collections for properties in propertiesRequiringIsEmptyCheck,
            // since those are backed by files which may be empty archives.
            // And being empty archives is not reflected in the fingerprint.
            return hasEmptyFingerprints(sourceFileProperties, propertyName -> !propertiesRequiringIsEmptyCheck.contains(propertyName))
                && hasEmptyInputFileCollections(work, propertiesRequiringIsEmptyCheck::contains);
        }
    }

    private static boolean hasEmptyFingerprints(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, Predicate<String> propertyNameFilter) {
        return sourceFileProperties.entrySet().stream()
            .filter(entry -> propertyNameFilter.test(entry.getKey()))
            .map(Map.Entry::getValue)
            .allMatch(CurrentFileCollectionFingerprint::isEmpty);
    }

    private static boolean hasEmptyInputFileCollections(UnitOfWork work, Predicate<String> propertyNameFilter) {
        EmptyCheckingVisitor visitor = new EmptyCheckingVisitor(propertyNameFilter);
        work.visitRegularInputs(visitor);
        return visitor.isAllEmpty();
    }

    private InputFingerprinter.Result fingerprintPrimaryInputs(UnitOfWork work, C context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFileFingerprints, ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots) {
        return work.getInputFingerprinter().fingerprintInputProperties(
            getKnownInputProperties(context),
            getKnownInputFileProperties(context),
            knownValueSnapshots,
            knownFileFingerprints,
            visitor -> work.visitRegularInputs(new InputVisitor() {
                @Override
                public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
                    if (behavior.shouldSkipWhenEmpty()) {
                        visitor.visitInputFileProperty(propertyName, behavior, value);
                    }
                }
            }));
    }

    abstract protected ImmutableSortedMap<String, ValueSnapshot> getKnownInputProperties(C context);

    abstract protected ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getKnownInputFileProperties(C context);

    @Nonnull
    private CachingResult skipExecutionWithEmptySources(UnitOfWork work, C context) {
        CachingResult result = performSkip(work, context);
        broadcastWorkInputs(work, true);
        return result;
    }

    abstract protected CachingResult performSkip(UnitOfWork work, C context);

    private CachingResult executeWithNonEmptySources(UnitOfWork work, C context) {
        broadcastWorkInputs(work, false);
        return delegate.execute(work, context);
    }

    private void broadcastWorkInputs(UnitOfWork work, boolean onlyPrimaryInputs) {
        workInputListeners.broadcastFileSystemInputsOf(work, onlyPrimaryInputs
            ? EnumSet.of(InputBehavior.PRIMARY)
            : EnumSet.allOf(InputBehavior.class));
    }

    private static class EmptyCheckingVisitor implements InputVisitor {
        private final Predicate<String> propertyNameFilter;
        private boolean allEmpty = true;

        public EmptyCheckingVisitor(Predicate<String> propertyNameFilter) {
            this.propertyNameFilter = propertyNameFilter;
        }

        @Override
        public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
            if (propertyNameFilter.test(propertyName)) {
                allEmpty = allEmpty && value.getFiles().isEmpty();
            }
        }

        public boolean isAllEmpty() {
            return allEmpty;
        }
    }
}
