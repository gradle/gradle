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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.cache.Cache;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterExecutionState;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;

public interface ExecutionEngine {
    Request createRequest(UnitOfWork work);

    interface Request {
        /**
         * Force the re-execution of the unit of work, disabling optimizations
         * like up-to-date checks, build cache and incremental execution.
         */
        void forceNonIncremental(String nonIncremental);

        /**
         * Set the validation context to use during execution.
         */
        void withValidationContext(WorkValidationContext validationContext);

        /**
         * Execute the unit of work using available optimizations like
         * up-to-date checks, build cache and incremental execution.
         */
        Result execute();

        /**
         * Load the unit of work from the given cache, or defer its execution.
         *
         * If the cache already contains the outputs for the given work, an already finished {@link Deferrable} will be returned.
         * Otherwise, the execution is wrapped in a not-yet-complete {@link Deferrable} to be evaluated later.
         * The work is looked up by its {@link UnitOfWork.Identity identity} in the given cache.
         */
        <T> Deferrable<Try<T>> executeDeferred(Cache<Identity, Try<T>> cache);
    }

    interface Result {
        Try<Execution> getExecution();

        CachingState getCachingState();

        // TODO Parametrize UnitOfWork with this type
        <T> Try<T> resolveOutputFromWorkspaceAs(Class<T> type);

        /**
         * A list of messages describing the first few reasons encountered that caused the work to be executed.
         * An empty list means the work was up-to-date and hasn't been executed.
         */
        ImmutableList<String> getExecutionReasons();

        /**
         * If a previously produced output was reused in some way, the reused output's origin metadata is returned.
         */
        Optional<OriginMetadata> getReusedOutputOriginMetadata();

        /**
         * State after execution.
         */
        @VisibleForTesting
        Optional<AfterExecutionState> getAfterExecutionState();
    }

    interface Execution {
        /**
         * Get how the outputs have been produced.
         */
        ExecutionOutcome getOutcome();

        /**
         * Get the object representing the produced output.
         * The type of value returned here depends on the {@link UnitOfWork} implmenetation.
         */
        // TODO Parametrize UnitOfWork with this generated result
        @Nullable
        Object getOutput(File workspace);

        /**
         * Whether the outputs of this execution should be stored in the build cache.
         */
        default boolean canStoreOutputsInCache() {
            return true;
        }

        static Execution skipped(ExecutionOutcome outcome, UnitOfWork work) {
            return new Execution() {
                @Override
                public ExecutionOutcome getOutcome() {
                    return outcome;
                }

                @Nullable
                @Override
                public Object getOutput(File workspace) {
                    return work.loadAlreadyProducedOutput(workspace);
                }
            };
        }
    }

    /**
     * The way the outputs have been produced.
     */
    enum ExecutionOutcome {
        /**
         * The outputs haven't been changed, because the work is already up-to-date
         * (i.e. its inputs and outputs match that of the previous execution in the
         * same workspace).
         */
        UP_TO_DATE,

        /**
         * The outputs of the work have been loaded from the build cache.
         */
        FROM_CACHE,

        /**
         * Executing the work was not necessary to produce the outputs.
         * This is usually due to the work having no inputs to process.
         */
        SHORT_CIRCUITED,

        /**
         * The work has been executed with information about the changes that happened since the previous execution.
         */
        EXECUTED_INCREMENTALLY,

        /**
         * The work has been executed with no incremental change information.
         */
        EXECUTED_NON_INCREMENTALLY
    }
}
