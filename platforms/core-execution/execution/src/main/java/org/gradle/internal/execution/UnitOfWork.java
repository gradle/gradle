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
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface UnitOfWork extends Describable {

    CachingDisabledReason NOT_WORTH_CACHING = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Not worth caching.");

    /**
     * Identifier of the type of the work used in build operations.
     * <p>
     * Values returned here become part of the contract of related build operations.
     */
    default Optional<String> getBuildOperationWorkType() {
        return Optional.empty();
    }

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

    /**
     * Parameter object for {@link #execute(ExecutionRequest)}.
     */
    interface ExecutionRequest {
        /**
         * The directory to produce outputs into.
         * <p>
         * Note: it's {@code null} for tasks as they don't currently have their own workspace.
         */
        File getWorkspace();

        /**
         * For work capable of incremental execution this is the object to query per-property changes through;
         * {@link Optional#empty()} for non-incremental-capable work.
         */
        Optional<InputChangesInternal> getInputChanges();

        /**
         * Output snapshots indexed by property from the previous execution;
         * {@link Optional#empty()} is information about a previous execution is not available.
         */
        Optional<ImmutableSortedMap<String, FileSystemSnapshot>> getPreviouslyProducedOutputs();
    }

    /**
     * The result of executing the user code.
     */
    interface WorkOutput {
        /**
         * Whether any significant amount of work has happened while executing the user code.
         * <p>
         * What amounts to "significant work" is up to the type of work implementation.
         */
        WorkResult getDidWork();

        /**
         * Implementation-specific output of executing the user code.
         */
        @Nullable
        Object getOutput(File workspace);

        /**
         * Whether this output should be stored in the build cache.
         */
        default boolean canStoreInCache() {
            return true;
        }
    }

    enum WorkResult {
        DID_WORK,
        DID_NO_WORK
    }

    /**
     * Loads output not produced during the current execution.
     * This can be output produced during a previous execution when the work is up-to-date,
     * or loaded from cache.
     */
    @Nullable
    Object loadAlreadyProducedOutput(File workspace);

    default Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    // TODO Move this to IncrementalUnitOfWork
    /**
     * Whether the work should be executed incrementally (if possible) or not.
     */
    default ExecutionBehavior getExecutionBehavior() {
        return ExecutionBehavior.NON_INCREMENTAL;
    }

    /**
     * The execution capability of the work: can be incremental, or non-incremental.
     * <p>
     * Note that incremental work can be executed non-incrementally if input changes
     * require it.
     */
    enum ExecutionBehavior {
        /**
         * Work can be executed incrementally, input changes for {@link InputBehavior#PRIMARY} and
         * {@link InputBehavior#INCREMENTAL} properties should be tracked.
         */
        INCREMENTAL,

        /**
         * Work is not capable of incremental execution, no need to track input changes.
         */
        NON_INCREMENTAL
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
        /**
         * Visit the implementation of the work. This is the type of the task or transform being executed.
         */
        void visitImplementation(Class<?> implementation);

        /**
         * Visits additional implementations related to the work. This is only used for additional task
         * actions that are added via {@code Task.doLast()} etc.
         */
        // TODO Make these additional implementations into identity inputs
        void visitAdditionalImplementation(ImplementationSnapshot implementation);
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
     * To visit all inputs one must call both {@link #visitIdentityInputs(InputVisitor)} as well as this method.
     */
    default void visitRegularInputs(InputVisitor visitor) {}

    /**
     * Visit outputs of the work in the given workspace.
     * <p>
     * Note: For tasks {@code workspace} is {@code null} for now.
     */
    void visitOutputs(File workspace, OutputVisitor visitor);

    interface InputVisitor {
        default void visitInputProperty(
            String propertyName,
            ValueSupplier value
        ) {}

        default void visitInputFileProperty(
            String propertyName,
            InputBehavior behavior,
            InputFileValueSupplier value
        ) {}
    }

    interface ValueSupplier {
        @Nullable
        Object getValue();
    }

    interface FileValueSupplier extends ValueSupplier {
        FileCollection getFiles();
    }

    class InputFileValueSupplier implements FileValueSupplier {
        private final Object value;
        private final FileNormalizer normalizer;
        private final DirectorySensitivity directorySensitivity;
        private final LineEndingSensitivity lineEndingSensitivity;
        private final Supplier<FileCollection> files;

        public InputFileValueSupplier(
            @Nullable Object value,
            FileNormalizer normalizer,
            DirectorySensitivity directorySensitivity,
            LineEndingSensitivity lineEndingSensitivity,
            Supplier<FileCollection> files
        ) {
            this.value = value;
            this.normalizer = normalizer;
            this.directorySensitivity = directorySensitivity;
            this.lineEndingSensitivity = lineEndingSensitivity;
            this.files = files;
        }

        @Nullable
        @Override
        public Object getValue() {
            return value;
        }

        public FileNormalizer getNormalizer() {
            return normalizer;
        }

        public DirectorySensitivity getDirectorySensitivity() {
            return directorySensitivity;
        }

        public LineEndingSensitivity getLineEndingNormalization() {
            return lineEndingSensitivity;
        }

        @Override
        public FileCollection getFiles() {
            return files.get();
        }
    }

    abstract class OutputFileValueSupplier implements FileValueSupplier {
        private final FileCollection files;

        public OutputFileValueSupplier(FileCollection files) {
            this.files = files;
        }

        public static OutputFileValueSupplier fromStatic(File root, FileCollection fileCollection) {
            return new OutputFileValueSupplier(fileCollection) {
                @NonNull
                @Override
                public File getValue() {
                    return root;
                }
            };
        }

        public static OutputFileValueSupplier fromSupplier(Supplier<File> root, FileCollection fileCollection) {
            return new OutputFileValueSupplier(fileCollection) {
                @NonNull
                @Override
                public File getValue() {
                    return root.get();
                }
            };
        }

        @NonNull
        @Override
        abstract public File getValue();

        @Override
        public FileCollection getFiles() {
            return files;
        }
    }

    interface OutputVisitor {
        default void visitOutputProperty(
            String propertyName,
            TreeType type,
            OutputFileValueSupplier value
        ) {}

        default void visitLocalState(File localStateRoot) {}

        default void visitDestroyable(File destroyableRoot) {}
    }

    /**
     * Return a reason to disable caching for this work.
     * When returning {@link Optional#empty()} if caching can still be disabled further down the pipeline.
     */
    default Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
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
     * Validate the work definition and configuration.
     */
    default void validate(WorkValidationContext validationContext) {}

    /**
     * Checks if outputs of the work are only consumed by inputs that declare a dependency on this unit of work.
     */
    default void checkOutputDependencies(WorkValidationContext validationContext) {}

    /**
     * Retuns a visitor that checks if inputs have declared dependencies on any consumed outputs.
     */
    default FileCollectionStructureVisitor getInputDependencyChecker(WorkValidationContext validationContext) {
        return FileCollectionStructureVisitor.NO_OP;
    }

    /**
     * Whether the outputs should be cleanup up when the work is executed non-incrementally.
     */
    default boolean shouldCleanupOutputsOnNonIncrementalExecution() {
        return true;
    }

    /**
     * Whether stale outputs should be cleanup up before execution.
     */
    default boolean shouldCleanupStaleOutputs() {
        return false;
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
