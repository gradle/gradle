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
import org.jspecify.annotations.Nullable;

/**
 * The outcome of configuration caching for a build, exposed via {@link BuildResult#getConfigurationCacheOutcome()}.
 * <p>
 * The concrete outcome is expressed by the subtype of this class. More subtypes may be added
 * in future Gradle versions.
 *
 * @since 9.9.0
 * @see BuildResult#getConfigurationCacheOutcome()
 */
@Incubating
@NullMarked
public abstract class ConfigurationCacheOutcome {

    private ConfigurationCacheOutcome() {
    }

    /**
     * The configuration cache was not enabled for the build.
     *
     * @since 9.9.0
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
     * @since 9.9.0
     */
    public static NotEnabled notEnabled() {
        return NotEnabled.INSTANCE;
    }

    /**
     * No configuration cache entry could be fully reused and an entry was stored.
     *
     * @since 9.9.0
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
     * @since 9.9.0
     */
    public static Stored stored() {
        return Stored.INSTANCE;
    }

    /**
     * A configuration cache entry was found and reused.
     *
     * @since 9.9.0
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
     * @since 9.9.0
     */
    public static Reused reused() {
        return Reused.INSTANCE;
    }

    /**
     * Storing a configuration cache entry was skipped on purpose and the build did not fail because of it,
     * e.g. because incompatible tasks were scheduled or no reusable entry was found while the cache is in read-only mode.
     *
     * @since 9.9.0
     */
    @Incubating
    public static final class StoreSkipped extends ConfigurationCacheOutcome {
        private static final StoreSkipped INSTANCE = new StoreSkipped();

        private StoreSkipped() {
        }
    }

    /**
     * Returns the outcome representing a configuration cache entry that was not stored on purpose.
     *
     * @return the outcome
     * @since 9.9.0
     */
    public static StoreSkipped storeSkipped() {
        return StoreSkipped.INSTANCE;
    }

    /**
     * No configuration cache entry was stored because the build failed, e.g. because of configuration cache problems,
     * a serialization error, or a failure before the entry could be stored.
     *
     * @since 9.9.0
     */
    @Incubating
    public static final class StoreFailed extends ConfigurationCacheOutcome {
        private static final StoreFailed INSTANCE = new StoreFailed();

        private StoreFailed() {
        }
    }

    /**
     * Returns the outcome representing a configuration cache entry that could not be stored.
     *
     * @return the outcome
     * @since 9.9.0
     */
    public static StoreFailed storeFailed() {
        return StoreFailed.INSTANCE;
    }

    /**
     * The configuration cache was enabled, but the outcome could not be determined, e.g. because the build
     * failed early or the target Gradle version reported an outcome that this TestKit version does not know.
     *
     * @since 9.9.0
     */
    @Incubating
    public static final class Undetermined extends ConfigurationCacheOutcome {
        private static final Undetermined INSTANCE = new Undetermined();

        private Undetermined() {
        }
    }

    /**
     * Returns the outcome representing an outcome that could not be determined.
     *
     * @return the outcome
     * @since 9.9.0
     */
    public static Undetermined undetermined() {
        return Undetermined.INSTANCE;
    }

    @Override
    public final boolean equals(@Nullable Object other) {
        return other != null && getClass().equals(other.getClass());
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
