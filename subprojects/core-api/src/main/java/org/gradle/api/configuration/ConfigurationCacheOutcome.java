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

package org.gradle.api.configuration;

import org.gradle.api.Incubating;

/**
 * The outcome of configuration caching for a build invocation.
 * <p>
 * Exposed to build logic via {@link org.gradle.api.flow.FlowProviders#getConfigurationCacheOutcome()},
 * whose value only becomes available once the scheduled work of the build has completed.
 * <p>
 * TestKit exposes the same information to plugin tests via
 * {@code org.gradle.testkit.runner.ConfigurationCacheOutcome}, which additionally reports
 * outcomes across Gradle versions.
 *
 * @see org.gradle.api.flow.FlowProviders#getConfigurationCacheOutcome()
 * @since 9.8.0
 */
@Incubating
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
     * A configuration cache entry was found and partially reused; the invalidated projects were
     * re-configured and the entry was updated.
     *
     * @since 9.8.0
     */
    UPDATED,

    /**
     * A configuration cache entry was written but discarded at the end of the build,
     * e.g. because of problems, a serialization error or incompatible tasks.
     *
     * @since 9.8.0
     */
    DISCARDED,

    /**
     * No configuration cache entry was stored on purpose: no reusable entry was found while the cache
     * is in read-only mode, or configuration caching was degraded gracefully because incompatible
     * tasks were scheduled.
     *
     * @since 9.8.0
     */
    NOT_STORED,

    /**
     * The configuration cache was enabled, but the build finished before the outcome was
     * determined, e.g. because it failed early.
     *
     * @since 9.8.0
     */
    UNDETERMINED
}
