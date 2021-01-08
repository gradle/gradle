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
import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.workspace.WorkspaceProvider;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface UnitOfWork extends Describable {
    /**
     * Determine the identity of the work unit that uniquely identifies it
     * among the other work units of the same type in the current build.
     */
    Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs);

    interface Identity {
        /**
         * The identity of the work unit that uniquely identifies it
         * among the other work units of the same type in the current build.
         */
        String getUniqueId();
    }

    /**
     * Executes the work synchronously.
     */
    WorkOutput execute(ExecutionRequest executionRequest);

    interface ExecutionRequest {
        File getWorkspace();

        Optional<InputChangesInternal> getInputChanges();

        Optional<ImmutableSortedMap<String, FileSystemSnapshot>> getPreviouslyProducedOutputs();
    }

    interface WorkOutput {
        WorkResult getDidWork();

        Object getOutput();
    }

    enum WorkResult {
        DID_WORK,
        DID_NO_WORK
    }

    default Object loadRestoredOutput(File workspace) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link WorkspaceProvider} to allocate a workspace to execution this work in.
     */
    WorkspaceProvider getWorkspaceProvider();

    default Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    default InputChangeTrackingStrategy getInputChangeTrackingStrategy() {
        return InputChangeTrackingStrategy.NONE;
    }

    /**
     * Capture the classloader of the work's implementation type.
     * There can be more than one type reported by the work; additional types are considered in visitation order.
     *
     * TODO Move this to {@link #visitInputs(InputVisitor)}
     */
    default void visitImplementations(ImplementationVisitor visitor) {
        visitor.visitImplementation(getClass());
    }

    interface ImplementationVisitor {
        void visitImplementation(Class<?> implementation);
        void visitImplementation(ImplementationSnapshot implementation);
    }

    /**
     * Visit all inputs of the work.
     */
    void visitInputs(InputVisitor visitor);

    interface InputVisitor {
        default void visitInputProperty(
            String propertyName,
            IdentityKind identity,
            ValueSupplier value
        ) {}

        default void visitInputFileProperty(
            String propertyName,
            InputPropertyType type,
            IdentityKind identity,
            @Nullable Object value,
            Supplier<CurrentFileCollectionFingerprint> fingerprinter
        ) {}
    }

    interface ValueSupplier {
        @Nullable
        Object getValue();
    }

    enum InputPropertyType {
        /**
         * Non-incremental inputs.
         */
        NON_INCREMENTAL(false, false),

        /**
         * Incremental inputs.
         */
        INCREMENTAL(true, false),

        /**
         * These are the primary inputs to the incremental work item;
         * if they are empty the work item shouldn't be executed.
         */
        PRIMARY(true, true);

        private final boolean incremental;
        private final boolean skipWhenEmpty;

        InputPropertyType(boolean incremental, boolean skipWhenEmpty) {
            this.incremental = incremental;
            this.skipWhenEmpty = skipWhenEmpty;
        }

        public boolean isIncremental() {
            return incremental;
        }

        public boolean isSkipWhenEmpty() {
            return skipWhenEmpty;
        }
    }

    enum IdentityKind {
        NON_IDENTITY, IDENTITY
    }

    void visitOutputs(File workspace, OutputVisitor visitor);

    interface OutputVisitor {
        default void visitOutputProperty(
            String propertyName,
            TreeType type,
            File root,
            FileCollection contents
        ) {}

        default void visitLocalState(File localStateRoot) {}

        default void visitDestroyable(File destroyableRoot) {}
    }

    default long markExecutionTime() {
        return 0;
    }

    /**
     * Validate the work definition and configuration.
     */
    default void validate(WorkValidationContext validationContext) {}

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
    default Optional<ExecutionOutcome> skipIfInputsEmpty(ImmutableSortedMap<String, FileSystemSnapshot> outputFilesAfterPreviousExecution) {
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
