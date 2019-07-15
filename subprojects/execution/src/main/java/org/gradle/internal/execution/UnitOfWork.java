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
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.Optional;

public interface UnitOfWork extends CacheableEntity {

    /**
     * Executes the work synchronously.
     */
    WorkResult execute(@Nullable InputChangesInternal inputChanges);

    Optional<Duration> getTimeout();

    InputChangeTrackingStrategy getInputChangeTrackingStrategy();

    void visitImplementations(ImplementationVisitor visitor);

    interface ImplementationVisitor {
        void visitImplementation(Class<?> implementation);
        void visitImplementation(ImplementationSnapshot implementation);
        void visitAdditionalImplementation(ImplementationSnapshot implementation);
    }

    void visitInputProperties(InputPropertyVisitor visitor);

    interface Property {
        String getName();
    }

    interface InputProperty extends Property {
        @Nullable
        Object getValue();
    }

    interface InputPropertyVisitor {
        void visitInputProperty(InputProperty property);
    }

    void visitInputFileProperties(InputFilePropertyVisitor visitor);

    interface InputFileProperty extends Property {
        @Nullable
        Object getValue();
        boolean isIncremental();
        CurrentFileCollectionFingerprint fingerprint();
    }

    interface InputFilePropertyVisitor {
        void visitInputFileProperty(InputFileProperty property);
    }

    void visitOutputProperties(OutputPropertyVisitor visitor);

    interface OutputFileProperty extends Property {
        TreeType getType();
        Iterable<File> getRoots();
    }

    interface OutputPropertyVisitor {
        void visitOutputProperty(OutputFileProperty property);
    }

    void visitLocalState(LocalStateVisitor visitor);

    interface LocalStateVisitor {
        void visitLocalStateRoot(File localStateRoot);
    }

    long markExecutionTime();

    /**
     * Return a reason to disable caching for this work.
     * When returning {@link Optional#empty()} if caching can still be disabled further down the pipeline.
     */
    Optional<CachingDisabledReason> shouldDisableCaching();

    /**
     * This is a temporary measure for Gradle tasks to track a legacy measurement of all input snapshotting together.
     */
    default void markSnapshottingInputsFinished(CachingState cachingState) {}

    /**
     * Returns whether the execution history should be stored.
     */
    default boolean isTaskHistoryMaintained() {
        return true;
    }

    /**
     * Is this work item allowed to load from the cache, or if we only allow it to be stored.
     */
    // TODO Make this part of CachingState instead
    boolean isAllowedToLoadFromCache();

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
    Optional<? extends Iterable<String>> getChangingOutputs();

    /**
     * Whether the current execution detected that there are overlapping outputs.
     */
    boolean hasOverlappingOutputs();

    /**
     * Whether the outputs should be cleanup up when the work is executed non-incrementally.
     */
    boolean shouldCleanupOutputsOnNonIncrementalExecution();

    /**
     * Returns the output snapshots after the current execution.
     */
    ImmutableSortedMap<String, FileSystemSnapshot> getOutputFileSnapshotsBeforeExecution();

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

    ExecutionHistoryStore getExecutionHistoryStore();

    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterOutputsGenerated();
}
