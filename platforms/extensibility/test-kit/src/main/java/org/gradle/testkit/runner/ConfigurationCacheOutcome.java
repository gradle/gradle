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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;

/**
 * The outcome of configuration caching for a build, exposed via {@link BuildResult#getConfigurationCacheOutcome()}.
 *
 * @since 9.8.0
 * @see BuildResult#getConfigurationCacheOutcome()
 */
@Incubating
@NullMarked
public enum ConfigurationCacheOutcome {

    /**
     * The configuration cache was not enabled for the build.
     *
     * @since 9.8.0
     */
    NOT_ENABLED,

    /**
     * No reusable configuration cache entry was found and a new entry was stored.
     *
     * @since 9.8.0
     */
    STORED,

    /**
     * A configuration cache entry was found and fully reused.
     *
     * @since 9.8.0
     */
    REUSED,

    /**
     * Storing a configuration cache entry failed, e.g. because of problems, a serialization
     * error, or because the build failed before the entry could be stored.
     *
     * @since 9.8.0
     */
    STORE_FAILED,

    /**
     * Storing a configuration cache entry was deliberately skipped, e.g. because an incompatible
     * task was scheduled, configuration caching was degraded gracefully, or no reusable entry
     * was found while the cache is in read-only mode.
     *
     * @since 9.8.0
     */
    STORE_SKIPPED,

    /**
     * The configuration cache was enabled, but the outcome could not be determined,
     * e.g. because the build failed early.
     * <p>
     * Also reported when the target Gradle version is newer than this TestKit version and
     * reports an outcome that this enum does not know about.
     *
     * @since 9.8.0
     */
    UNDETERMINED
}
