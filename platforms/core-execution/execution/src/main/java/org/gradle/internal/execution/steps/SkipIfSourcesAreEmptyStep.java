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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionOutput;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.WorkResult;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SkipIfSourcesAreEmptyStep implements Step<MutableWorkspaceContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipIfSourcesAreEmptyStep.class);

    private final OutputChangeListener outputChangeListener;
    private final WorkInputListeners workInputListeners;
    private final Supplier<OutputsCleaner> outputsCleanerSupplier;
    private final Step<? super InputChangesContext, ? extends Result> shortCutDelegate;
    private final Step<? super WorkDeterminedContext, ? extends CachingResult> delegate;

    public SkipIfSourcesAreEmptyStep(
        OutputChangeListener outputChangeListener,
        WorkInputListeners workInputListeners,
        Supplier<OutputsCleaner> outputsCleanerSupplier,
        Step<? super InputChangesContext, ? extends Result> directExecutionDelegate,
        Step<? super WorkDeterminedContext, ? extends CachingResult> delegate
    ) {
        this.outputChangeListener = outputChangeListener;
        this.workInputListeners = workInputListeners;
        this.outputsCleanerSupplier = outputsCleanerSupplier;
        this.shortCutDelegate = directExecutionDelegate;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, MutableWorkspaceContext context) {
        InputFingerprinter.Result newInputs = fingerprintPrimaryInputs(work, context, context.getInputFileProperties(), context.getInputProperties());
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties = newInputs.getFileFingerprints();

        if (hasNoSourceProperties(sourceFileProperties)) {
            broadcastWorkInputs(work, false);
            return delegate.execute(work, new WorkDeterminedContext(context, work));
        } else {
            // We have some source properties annotated with `@SkipWhenEmpty`
            if (hasOnlyEmptySources(sourceFileProperties, newInputs.getPropertiesRequiringIsEmptyCheck(), work)) {
                // All such properties are empty
                broadcastWorkInputs(work, true);
                return shortcutExecution(work, context, sourceFileProperties);
            } else {
                // We have some actual sources
                broadcastWorkInputs(work, false);
                return delegate.execute(work, new WorkDeterminedContext(context, newInputs.getAllFileFingerprints(), work));
            }
        }
    }

    private static boolean hasNoSourceProperties(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties) {
        return sourceFileProperties.isEmpty();
    }

    private static boolean hasOnlyEmptySources(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, ImmutableSet<String> propertiesRequiringIsEmptyCheck, UnitOfWork work) {
        if (propertiesRequiringIsEmptyCheck.isEmpty()) {
            return sourceFileProperties.values().stream()
                .allMatch(CurrentFileCollectionFingerprint::isEmpty);
        } else {
            // We need to check the underlying file collections for properties in propertiesRequiringIsEmptyCheck,
            // since those are backed by files which may be empty archives.
            // And being empty archives is not reflected in the fingerprint.
            return hasOnlyEmptyFingerprints(sourceFileProperties, propertyName -> !propertiesRequiringIsEmptyCheck.contains(propertyName))
                && hasOnlyEmptyInputFileCollections(work, propertiesRequiringIsEmptyCheck::contains);
        }
    }

    private static boolean hasOnlyEmptyFingerprints(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, Predicate<String> propertyNameFilter) {
        return sourceFileProperties.entrySet().stream()
            .filter(entry -> propertyNameFilter.test(entry.getKey()))
            .map(Map.Entry::getValue)
            .allMatch(CurrentFileCollectionFingerprint::isEmpty);
    }

    private static boolean hasOnlyEmptyInputFileCollections(UnitOfWork work, Predicate<String> propertyNameFilter) {
        EmptyCheckingVisitor visitor = new EmptyCheckingVisitor(propertyNameFilter);
        work.visitRegularInputs(visitor);
        return visitor.isAllEmpty();
    }

    private static InputFingerprinter.Result fingerprintPrimaryInputs(UnitOfWork work, MutableWorkspaceContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFileFingerprints, ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots) {
        return work.getInputFingerprinter().fingerprintInputProperties(
            context.getPreviousExecutionState()
                .map(PreviousExecutionState::getInputProperties)
                .orElse(ImmutableSortedMap.of()),
            context.getPreviousExecutionState()
                .map(PreviousExecutionState::getInputFileProperties)
                .orElse(ImmutableSortedMap.of()),
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

    @Nonnull
    private CachingResult shortcutExecution(UnitOfWork work, MutableWorkspaceContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties) {
        return context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .map(outputFilesAfterPreviousExecution -> {
                LOGGER.info("Cleaning previous output of {} as it has no source files.", work.getDisplayName());
                InputChangesContext delegateContext = new InputChangesContext(
                    new ValidationFinishedContext(
                        new BeforeExecutionContext(
                            new WorkDeterminedContext(
                                context,
                                sourceFileProperties,
                                request -> cleanPreviousOutputs(work, context.getMutableWorkspaceLocation(), outputFilesAfterPreviousExecution)),
                            null
                        ),
                        ImmutableList.of()
                    ),
                    null
                );
                Result delegateResult = shortCutDelegate.execute(work, delegateContext);
                return new CachingResult(
                    new UpToDateResult(
                        new AfterExecutionResult(delegateResult,
                            null),
                        ImmutableList.of("Previous outputs were present"),
                        null
                    ),
                    CachingState.NOT_DETERMINED
                );
            })
            .orElseGet(() -> {
                LOGGER.info("Skipping {} as it has no source files and no previous output files.", work.getDisplayName());

                Try<Execution> execution = Try.successful(new Execution() {
                    @Override
                    public ExecutionOutcome getOutcome() {
                        return ExecutionOutcome.SHORT_CIRCUITED;
                    }

                    @Override
                    public Object getOutput() {
                        return work.loadAlreadyProducedOutput(context.getMutableWorkspaceLocation());
                    }
                });
                return new CachingResult(Duration.ZERO, execution, null, ImmutableList.of(), null, CachingState.NOT_DETERMINED);
            });
    }

    private void broadcastWorkInputs(UnitOfWork work, boolean onlyPrimaryInputs) {
        workInputListeners.broadcastFileSystemInputsOf(work, onlyPrimaryInputs
            ? EnumSet.of(InputBehavior.PRIMARY)
            : EnumSet.allOf(InputBehavior.class));
    }

    private ExecutionOutput cleanPreviousOutputs(UnitOfWork work, File workspace, Map<String, FileSystemSnapshot> outputFileSnapshots) {
        OutputsCleaner outputsCleaner = outputsCleanerSupplier.get();
        for (FileSystemSnapshot outputFileSnapshot : outputFileSnapshots.values()) {
            try {
                outputChangeListener.invalidateCachesFor(SnapshotUtil.rootIndex(outputFileSnapshot).keySet());
                outputsCleaner.cleanupOutputs(outputFileSnapshot);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new ExecutionOutput() {
            @Override
            public WorkResult getDidWork() {
                return outputsCleaner.getDidWork()
                    ? WorkResult.DID_WORK
                    : WorkResult.DID_NO_WORK;
            }

            @Nullable
            @Override
            public Object getOutput() {
                return work.loadAlreadyProducedOutput(workspace);
            }
        };
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
