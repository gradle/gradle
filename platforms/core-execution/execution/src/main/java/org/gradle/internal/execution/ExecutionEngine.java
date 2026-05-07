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
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.ExecutionOutputState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Optional;

@ServiceScope(Scope.Build.class)
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
         * The work is looked up by its {@link Identity identity} in the given cache.
         */
        <T> Deferrable<Try<T>> executeDeferred(Cache<Identity, DeferredResult<T>> cache);
    }

    interface Result {
        Try<Execution> getExecution();

        CachingState getCachingState();

        /**
         * Get the output of the work. For immutable work this is resolved from the immutable workspace.
         */
        // TODO Parametrize UnitOfWork with this type
        <T> Try<T> getOutputAs(Class<T> type);

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
        Optional<ExecutionOutputState> getAfterExecutionOutputState();
    }
}
