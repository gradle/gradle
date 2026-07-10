/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider;
import org.gradle.internal.properties.InputBehavior;

import java.util.Optional;

/**
 * A unit of work that can be executed multiple times in the same workspace.
 * Such work can reuse outputs from a previous execution.
 */
public interface MutableUnitOfWork extends UnitOfWork {
    /**
     * Returns the {@link MutableWorkspaceProvider} to allocate a workspace to execution this work in.
     */
    MutableWorkspaceProvider getWorkspaceProvider();

    /**
     * The store for previous execution states if we are to keep track.
     */
    Optional<ExecutionHistoryStore> getHistory();

    /**
     * Whether the work should be executed incrementally (if possible) or not.
     */
    ExecutionBehavior getExecutionBehavior();

    /**
     * Whether overlapping outputs should be allowed or ignored.
     */
    default OverlappingOutputHandling getOverlappingOutputHandling() {
        return OverlappingOutputHandling.IGNORE_OVERLAPS;
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
}
