/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.reflect.TypeValidationContext;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public interface UnitOfWork extends CacheableEntity, Describable {

    /**
     * Executes the work synchronously.
     */
    WorkResult execute(@Nullable InputChangesInternal inputChanges, InputChangesContext context);

    default Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    default InputChangeTrackingStrategy getInputChangeTrackingStrategy() {
        return InputChangeTrackingStrategy.NONE;
    }

    void visitImplementations(ImplementationVisitor visitor);

    interface ImplementationVisitor {
        void visitImplementation(Class<?> implementation);
        void visitImplementation(ImplementationSnapshot implementation);
        void visitAdditionalImplementation(ImplementationSnapshot implementation);
    }

    void visitInputProperties(InputPropertyVisitor visitor);

    interface InputPropertyVisitor {
        void visitInputProperty(String propertyName, Object value);
    }

    void visitInputFileProperties(InputFilePropertyVisitor visitor);

    interface InputFilePropertyVisitor {
        void visitInputFileProperty(String propertyName, @Nullable Object value, boolean incremental, Supplier<CurrentFileCollectionFingerprint> fingerprinter);
    }

    void visitOutputProperties(OutputPropertyVisitor visitor);

    interface OutputPropertyVisitor {
        void visitOutputProperty(String propertyName, TreeType type, File root);
    }

    default void visitLocalState(LocalStateVisitor visitor) {}

    interface LocalStateVisitor {
        void visitLocalStateRoot(File localStateRoot);
    }

    long markExecutionTime();

    /**
     * Validate the work definition and configuration.
     */
    void validate(WorkValidationContext validationContext);

    interface WorkValidationContext {
        TypeValidationContext createContextFor(Class<?> type, boolean cacheable);
    }

    /**
     * Return a reason to disable caching for this work.
     * When returning {@link Optional#empty()} if caching can still be disabled further down the pipeline.
     */
    default Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
        return Optional.empty();
    }

    /**
     * Checks if this work has empty inputs. If the work cannot be skipped, {@link Optional#empty()} is returned.
     * If it can, either {@link ExecutionOutcome#EXECUTED_NON_INCREMENTALLY} or {@link ExecutionOutcome#SHORT_CIRCUITED} is
     * returned depending on whether cleanup of existing outputs had to be performed.
     */
    default Optional<ExecutionOutcome> skipIfInputsEmpty(ImmutableSortedMap<String, FileCollectionFingerprint> outputFilesAfterPreviousExecution) {
        return Optional.empty();
    }

    /**
     * Is this work item allowed to load from the cache, or if we only allow it to be stored.
     */
    // TODO Make this part of CachingState instead
    default boolean isAllowedToLoadFromCache() {
        return true;
    }

    /**
     * Paths to locations changed by the unit of work.
     *
     * <p>
     * We don't want to invalidate the whole file system mirror for artifact transformations, since I know exactly which parts need to be invalidated.
     * For tasks though, we still need to invalidate everything.
     * </p>
     *
     * @return {@link Optional#empty()} if the unit of work cannot guarantee that only some files have been changed or an iterable of the paths which were changed by the unit of work.
     */
    Iterable<String> getChangingOutputs();

    /**
     * Whether overlapping outputs should be allowed or ignored.
     */
    default OverlappingOutputHandling getOverlappingOutputHandling() {
        return OverlappingOutputHandling.IGNORE_OVERLAPS;
    }

    enum OverlappingOutputHandling {
        /**
         * Overlapping outputs are detected and handled.
         */
        DETECT_OVERLAPS,

        /**
         * Overlapping outputs are not detected.
         */
        IGNORE_OVERLAPS
    }

    /**
     * Whether the outputs should be cleanup up when the work is executed non-incrementally.
     */
    default boolean shouldCleanupOutputsOnNonIncrementalExecution() {
        return true;
    }

    /**
     * Takes a snapshot of the outputs before execution.
     */
    ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputsBeforeExecution();

    /**
     * Takes a snapshot of the outputs after execution.
     */
    ImmutableSortedMap<String, FileSystemSnapshot> snapshotOutputsAfterExecution();

    /**
     * Convert to fingerprints and filter out missing roots.
     */
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintAndFilterOutputSnapshots(
        ImmutableSortedMap<String, FileCollectionFingerprint> afterPreviousExecutionOutputFingerprints,
        ImmutableSortedMap<String, FileSystemSnapshot> beforeExecutionOutputSnapshots,
        ImmutableSortedMap<String, FileSystemSnapshot> afterExecutionOutputSnapshots,
        boolean hasDetectedOverlappingOutputs
    );

    enum WorkResult {
        DID_WORK,
        DID_NO_WORK
    }

    enum InputChangeTrackingStrategy {
        /**
         * No incremental parameters, nothing to track.
         */
        NONE(false),
        /**
         * Only the incremental parameters should be tracked for input changes.
         */
        INCREMENTAL_PARAMETERS(true),
        /**
         * All parameters are considered incremental.
         *
         * @deprecated Only used for {@code IncrementalTaskInputs}. Should be removed once {@code IncrementalTaskInputs} is gone.
         */
        @SuppressWarnings("DeprecatedIsStillUsed")
        @Deprecated
        ALL_PARAMETERS(true);

        private final boolean requiresInputChanges;

        InputChangeTrackingStrategy(boolean requiresInputChanges) {
            this.requiresInputChanges = requiresInputChanges;
        }

        public boolean requiresInputChanges() {
            return requiresInputChanges;
        }
    }

    /**
     * Returns the {@link ExecutionHistoryStore} to use to store the execution state of this work.
     * When {@link Optional#empty()} no execution history will be maintained.
     */
    default Optional<ExecutionHistoryStore> getExecutionHistoryStore() {
        return Optional.empty();
    }

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void markLegacySnapshottingInputsStarted() {}

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void markLegacySnapshottingInputsFinished(CachingState cachingState) {}

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void ensureLegacySnapshottingInputsClosed() {}
}
