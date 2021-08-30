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
import org.gradle.internal.execution.OutputSnapshotter.OutputFileSnapshottingException;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputFileFingerprintingException;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputVisitor;
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
     */
    default void visitImplementations(ImplementationVisitor visitor) {
        visitor.visitImplementation(getClass());
    }

    // TODO Move this to {@link InputVisitor}
    interface ImplementationVisitor {
        void visitImplementation(Class<?> implementation);
        void visitImplementation(ImplementationSnapshot implementation);
    }

    /**
     * Returns the fingerprinter used to fingerprint inputs.
     */
    InputFingerprinter getInputFingerprinter();

    /**
     * Visit identity inputs of the work.
     *
     * These are inputs that are passed to {@link #identify(Map, Map)} to calculate the identity of the work.
     * These are more expensive to calculate than regular inputs as they need to be calculated even if the execution of the work is short circuited by an identity cache.
     * They also cannot reuse snapshots taken during previous executions.
     * Because of these reasons only capture inputs as identity if they are actually used to calculate the identity of the work.
     * Any non-identity inputs should be visited when calling {@link #visitRegularInputs(InputVisitor)}.
     */
    default void visitIdentityInputs(InputVisitor visitor) {}

    /**
     * Visit regular inputs of the work.
     *
     * Regular inputs are inputs that are not used to calculate the identity of the work, but used to check up-to-dateness or to calculate the cache key.
     * To visit all inputs one must call both {@link #visitIdentityInputs(InputVisitor)} and {@link #visitRegularInputs(InputVisitor)}.
     */
    default void visitRegularInputs(InputVisitor visitor) {}

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

    /**
     * Handles when an input cannot be read while fingerprinting.
     */
    default void handleUnreadableInputs(InputFileFingerprintingException ex) {
        throw ex;
    }

    /**
     * Handles when an output cannot be read while snapshotting.
     */
    default void handleUnreadableOutputs(OutputFileSnapshottingException ex) {
        throw ex;
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
    default Optional<ExecutionOutcome> skipIfInputsEmpty(ImmutableSortedMap<String, FileSystemSnapshot> previousOutputFiles) {
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

    /**
     * Returns a type origin inspector, which is used for diagnostics (e.g error messages) to provide
     * more context about the origin of types (for example in what plugin a type is defined)
     */
    default WorkValidationContext.TypeOriginInspector getTypeOriginInspector() {
        return WorkValidationContext.TypeOriginInspector.NO_OP;
    }
}
