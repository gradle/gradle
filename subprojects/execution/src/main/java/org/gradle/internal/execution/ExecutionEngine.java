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
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.cache.Cache;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.UnitOfWork.Identity;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Optional;
import java.util.function.Supplier;

public interface ExecutionEngine {
    /**
     * Execute the given unit of work using available optimizations like
     * up-to-date checks, build cache and incremental execution.
     */
    Result execute(UnitOfWork work);

    /**
     * Force the re-execution of the given unit of work disabling optimizations
     * like up-to-date checks, build cache and incremental execution.
     *
     * @param reason the reason to report for rebuilding the given unit of work.
     */
    Result rebuild(UnitOfWork work, String reason);

    /**
     * Load the given unit from the given cache, or defer its execution.
     *
     * If the cache already contains the outputs for the given work, it is passed directly to {@link DeferredExecutionHandler#processCachedOutput(Try)}.
     * Otherwise the execution is wrapped in deferred via {@link DeferredExecutionHandler#processDeferredOutput(Supplier)}.
     * The work is looked up by its {@link UnitOfWork.Identity identity} in the given cache.
     */
    <T, O> T getFromIdentityCacheOrDeferExecution(UnitOfWork work, Cache<Identity, Try<O>> cache, DeferredExecutionHandler<O, T> handler);

    interface Result {
        Try<ExecutionResult> getExecutionResult();

        CachingState getCachingState();

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
         * Snapshots of the roots of output properties.
         *
         * Does not include any overlapping outputs <em>not</em> produced by the work.
         */
        @VisibleForTesting
        ImmutableSortedMap<String, FileSystemSnapshot> getOutputFilesProduceByWork();
    }
}
