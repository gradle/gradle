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
import org.jspecify.annotations.Nullable;

/**
 * The outcome of configuration caching for a build invocation.
 * <p>
 * Exposed to build logic via {@link org.gradle.api.flow.FlowProviders#getConfigurationCacheOutcome()},
 * whose value only becomes available once the scheduled work of the build has completed.
 * <p>
 * The concrete outcome is expressed by the subtype of this class. More subtypes may be added
 * in future Gradle versions.
 * <p>
 * TestKit exposes similar information to plugin tests via
 * {@code org.gradle.testkit.runner.ConfigurationCacheOutcome}.
 *
 * @see org.gradle.api.flow.FlowProviders#getConfigurationCacheOutcome()
 * @since 9.8.0
 */
@Incubating
public abstract class ConfigurationCacheOutcome {

    private ConfigurationCacheOutcome() {
    }

    /**
     * The configuration cache was not enabled for the build.
     *
     * @since 9.8.0
     */
    @Incubating
    public static final class NotEnabled extends ConfigurationCacheOutcome {
        private static final NotEnabled INSTANCE = new NotEnabled();

        private NotEnabled() {
        }
    }

    /**
     * Returns the outcome representing a build for which the configuration cache was not enabled.
     *
     * @return the outcome
     * @since 9.8.0
     */
    public static NotEnabled notEnabled() {
        return NotEnabled.INSTANCE;
    }

    /**
     * No reusable configuration cache entry was found and a new entry was stored.
     *
     * @since 9.8.0
     */
    @Incubating
    public static final class Stored extends ConfigurationCacheOutcome {
        private static final Stored INSTANCE = new Stored();

        private Stored() {
        }
    }

    /**
     * Returns the outcome representing a stored configuration cache entry.
     *
     * @return the outcome
     * @since 9.8.0
     */
    public static Stored stored() {
        return Stored.INSTANCE;
    }

    /**
     * A configuration cache entry was found and reused.
     *
     * @since 9.8.0
     */
    @Incubating
    public static final class Reused extends ConfigurationCacheOutcome {
        private static final Reused INSTANCE = new Reused();

        private Reused() {
        }
    }

    /**
     * Returns the outcome representing a reused configuration cache entry.
     *
     * @return the outcome
     * @since 9.8.0
     */
    public static Reused reused() {
        return Reused.INSTANCE;
    }

    /**
     * Storing a configuration cache entry failed, e.g. because of configuration cache problems,
     * a serialization error, or because the build failed before the entry could be stored.
     *
     * @since 9.8.0
     */
    @Incubating
    public static final class StoreFailed extends ConfigurationCacheOutcome {
        private static final StoreFailed INSTANCE = new StoreFailed();

        private StoreFailed() {
        }
    }

    /**
     * Returns the outcome representing a failure to store a configuration cache entry.
     *
     * @return the outcome
     * @since 9.8.0
     */
    public static StoreFailed storeFailed() {
        return StoreFailed.INSTANCE;
    }

    /**
     * Storing a configuration cache entry was deliberately skipped, e.g. because an incompatible
     * task was scheduled, configuration caching was degraded gracefully, or no reusable entry was
     * found while the cache is in read-only mode.
     *
     * @since 9.8.0
     */
    @Incubating
    public static final class StoreSkipped extends ConfigurationCacheOutcome {
        private static final StoreSkipped INSTANCE = new StoreSkipped();

        private StoreSkipped() {
        }
    }

    /**
     * Returns the outcome representing a deliberately skipped configuration cache entry store.
     *
     * @return the outcome
     * @since 9.8.0
     */
    public static StoreSkipped storeSkipped() {
        return StoreSkipped.INSTANCE;
    }

    @Override
    public final boolean equals(@Nullable Object rhs) {
        return rhs != null && getClass().equals(rhs.getClass());
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName();
    }
}
