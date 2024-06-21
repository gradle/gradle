/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.configurationcache;

import org.gradle.internal.operations.BuildOperationType;

import java.util.List;

/**
 * Details about the configuration cache fingerprint check operation. This operation is only emitted when the configuration cache entry is available.
 *
 * @since 8.10
 */
public final class ConfigurationCacheCheckFingerprintBuildOperationType implements BuildOperationType<ConfigurationCacheCheckFingerprintBuildOperationType.Details, ConfigurationCacheCheckFingerprintBuildOperationType.Result> {
    /**
     * Build operation details.
     *
     * @since 8.10
     */
    public interface Details {}

    /**
     * Result of the fingerprint check. The fingerprint can be:
     * <ul>
     *     <li><b>Valid</b>: the cached configuration can be fully reused.
     *     Both {@link #getBuildInvalidationReasons()} and {@link #getProjectInvalidationReasons()} are empty.</li>
     *
     *     <li><b>Partially invalidated</b>: some projects are invalidated and have to be reconfigured.
     *     The rest of the configuration data is still reused.
     *     The {@link #getBuildInvalidationReasons()} list is empty.
     *     The {@link #getProjectInvalidationReasons()} list contains information about invalidated projects.</li>
     *
     *     <li><b>Fully invalidated</b>: some of the build-level inputs has changed. The entry is discarded and configuration phase reruns from scratch.
     *     The {@link #getBuildInvalidationReasons()} list contains the invalidation reasons.
     *     The {@link #getProjectInvalidationReasons()} list is empty.</li>
     * </ul>
     *
     * @since 8.10
     */
    public interface Result {
        /**
         * Returns the list of the build fingerprint invalidation reasons. Can be empty if all build-level inputs are up-to-date.
         * The entry may still be partially invalidated if any of the project-scoped fingerprints are invalid.
         * <p>
         * Not all invalidation reasons may be returned. However, if the entry is invalidated, at least one reason is listed.
         * <p>
         * The first invalidation in the list is what Gradle shows to the user.
         *
         * @return the list of invalidation reasons
         * @since 8.10
         */
        List<FingerprintInvalidationReason> getBuildInvalidationReasons();

        /**
         * Returns a list of project-level invalidation reasons. Only contains entries for projects that were invalidated.
         * This list is empty if the whole build is invalidated, i.e. when {@link #getBuildInvalidationReasons()} returns a non-empty list.
         * <p>
         * All invalidated project have entries in this list, however, not all invalidation reasons may be listed.
         * <p>
         * The first invalidation of the first project in the list is what Gradle shows to the user.
         *
         * @return a list of invalidation projects with their invalidation reasons
         * @since 8.10
         */
        List<ProjectInvalidationReasons> getProjectInvalidationReasons();
    }

    /**
     * Contains data of a single invalidated project.
     *
     * @since 8.10
     */
    public interface ProjectInvalidationReasons {
        /**
         * Returns the identity path of the invalidated project.
         *
         * @return the identity path
         * @since 8.10
         */
        String getIdentityPath();

        /**
         * Returns the list of invalidation reasons. At least one invalidation is always present.
         *
         * @return the list of invalidation reasons
         * @since 8.10
         */
        List<FingerprintInvalidationReason> getInvalidationReasons();
    }

    /**
     * Represents the invalidation reason.
     *
     * @since 8.10
     */
    public interface FingerprintInvalidationReason {
        /**
         * Returns a simple text representation of the invalidation reason.
         *
         * @return the invalidation reason as text
         * @since 8.10
         */
        String getMessage();
    }
}
