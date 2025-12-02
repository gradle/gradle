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

import org.gradle.api.Describable;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.caching.CachingDisabledReasonCategory;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public interface UnitOfWork extends Describable {

    /**
     * Determine the identity of the work unit that uniquely identifies it
     * among the other work units of the same type in the current build.
     */
    Identity identify(Map<String, ValueSnapshot> scalarInputs, Map<String, CurrentFileCollectionFingerprint> fileInputs);

    /**
     * Executes the work synchronously.
     */
    WorkOutput execute(ExecutionContext executionContext);

    /**
     * Validate the work definition and configuration.
     */
    default void validate(WorkValidationContext validationContext) {}

    /**
     * Capture the classloader of the work's implementation type.
     * There can be more than one type reported by the work; additional types are considered in visitation order.
     */
    default void visitImplementations(ImplementationVisitor visitor) {
        visitor.visitImplementation(getClass());
    }

    /**
     * Visit immutable inputs of the work.
     *
     * These are inputs that are passed to {@link #identify(Map, Map)} to calculate the identity of the work.
     * Any mutable inputs should be visited when calling {@link #visitMutableInputs(InputVisitor)}.
     */
    default void visitImmutableInputs(InputVisitor visitor) {}

    /**
     * Visit mutable inputs of the work.
     *
     * Mutable inputs are not used to calculate the identity of the work, but used to check up-to-dateness or to calculate the cache key.
     * To visit all inputs one must call both {@link #visitImmutableInputs(InputVisitor)} as well as this method.
     */
    // TODO Move to MutableUnitOfWork
    default void visitMutableInputs(InputVisitor visitor) {}

    /**
     * Visit outputs of the work in the given workspace.
     * <p>
     * Note: For tasks {@code workspace} is {@code null} for now.
     */
    void visitOutputs(File workspace, OutputVisitor visitor);

    /**
     * Loads output not produced during the current execution.
     * This can be output produced during a previous execution when the work is up-to-date,
     * or loaded from cache.
     */
    @Nullable
    Object loadAlreadyProducedOutput(File workspace);

    /**
     * Return a reason to disable caching for this work.
     * When returning {@link Optional#empty()} if caching can still be disabled further down the pipeline.
     */
    // TODO Split this between mutable and immutable work, only keep overlapping outputs for mutable
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

    default Optional<Duration> getTimeout() {
        return Optional.empty();
    }

    /**
     * Checks if outputs of the work are only consumed by inputs that declare a dependency on this unit of work.
     */
    default void checkOutputDependencies(WorkValidationContext validationContext) {}

    /**
     * Returns the fingerprinter used to fingerprint inputs.
     */
    InputFingerprinter getInputFingerprinter();

    /**
     * Returns a visitor that checks if inputs have declared dependencies on any consumed outputs.
     */
    default FileCollectionStructureVisitor getInputDependencyChecker(WorkValidationContext validationContext) {
        return FileCollectionStructureVisitor.NO_OP;
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
     * Identifier of the type of the work used in build operations.
     * <p>
     * Values returned here become part of the contract of related build operations.
     */
    default Optional<String> getBuildOperationWorkType() {
        return Optional.empty();
    }

    CachingDisabledReason NOT_WORTH_CACHING = new CachingDisabledReason(CachingDisabledReasonCategory.NOT_CACHEABLE, "Not worth caching.");
}
