/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.operations.configuration;

import org.gradle.internal.operations.BuildOperationType;

/**
 * The final outcome of configuration caching for a build invocation.
 * <p>
 * Emitted once at the end of a root build when the configuration cache is enabled.
 * Fails with the failure that prevented the entry from being stored, when there is one.
 *
 * @since 9.9.0
 */
public final class ConfigurationCacheEntryOutcomeBuildOperationType implements BuildOperationType<ConfigurationCacheEntryOutcomeBuildOperationType.Details, ConfigurationCacheEntryOutcomeBuildOperationType.Result> {

    /**
     * Build operation details.
     *
     * @since 9.9.0
     */
    public interface Details {}

    /**
     * The final outcome of configuration caching for this build invocation.
     *
     * @since 9.9.0
     */
    public interface Result {

        /**
         * Returns the outcome for the configuration cache entry.
         *
         * @return the outcome
         * @since 9.9.0
         */
        Outcome getOutcome();

        /**
         * Returns the number of configuration cache problems reported to the console for this build invocation.
         *
         * @return the problem count
         * @since 9.9.0
         */
        int getProblemCount();
    }

    /**
     * The final fate of the configuration cache entry.
     *
     * @since 9.9.0
     */
    public enum Outcome {
        /**
         * No entry could be fully reused and an entry was stored.
         *
         * @since 9.9.0
         */
        STORED,
        /**
         * An entry was found and reused.
         *
         * @since 9.9.0
         */
        REUSED,
        /**
         * Storing an entry was skipped on purpose and the build did not fail because of it, e.g. because
         * incompatible tasks were scheduled or no reusable entry was found while the cache is in read-only mode.
         *
         * @since 9.9.0
         */
        STORE_SKIPPED,
        /**
         * No entry was stored because the build failed, e.g. because of configuration cache problems,
         * a serialization error, or a failure before the entry could be stored.
         *
         * @since 9.9.0
         */
        STORE_FAILED,
        /**
         * The build finished before the configuration cache outcome was determined, e.g. because it failed early.
         *
         * @since 9.9.0
         */
        UNDETERMINED
    }
}
