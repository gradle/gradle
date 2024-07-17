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

package org.gradle.operations.configuration;

import org.gradle.internal.operations.BuildOperationType;

import java.util.List;

/**
 * Details about the configuration cache fingerprint check operation.
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
     *     <li><b>Not found</b>: there is no cached configuration for the key. Corresponds to {@link CheckStatus#NOT_FOUND}.
     *     Both {@link #getBuildInvalidationReasons()} and {@link #getProjectInvalidationReasons()} are empty.</li>
     *     <li><b>Valid</b>: the cached configuration can be fully reused. Corresponds to {@link CheckStatus#VALID}.
     *     Both {@link #getBuildInvalidationReasons()} and {@link #getProjectInvalidationReasons()} are empty.</li>
     *
     *     <li><b>Partially invalidated</b>: some projects are invalidated and have to be reconfigured.
     *     The configuration is rebuilt but cached data of the unaffected projects is reused.
     *     Corresponds to {@link CheckStatus#PARTIAL}.
     *     The {@link #getBuildInvalidationReasons()} list is empty.
     *     The {@link #getProjectInvalidationReasons()} list contains information about invalidated projects.</li>
     *
     *     <li><b>Fully invalidated</b>: some of the build-level inputs has changed. The entry is discarded and configuration phase reruns from scratch.
     *     Corresponds to {@link CheckStatus#INVALID}.
     *     The {@link #getBuildInvalidationReasons()} list contains the invalidation reasons.
     *     The {@link #getProjectInvalidationReasons()} list is empty.</li>
     * </ul>
     *
     * @since 8.10
     */
    public interface Result {

        /**
         * Returns the overall status of the fingerprint check.
         *
         * @return the status
         * @since 8.10
         */
        CheckStatus getStatus();

        /**
         * Returns the list of the build fingerprint invalidation reasons. Can be empty if all build-level inputs are up-to-date.
         * The entry may still be partially invalidated if any of the project-scoped fingerprints are invalid.
         * <p>
         * Not all invalidation reasons may be returned. However, if the entry is invalidated, at least one reason is listed.
         * <p>
         * The first invalidation of the first build in the list is what Gradle shows to the user.
         *
         * @return the list of invalidation reasons
         * @since 8.10
         */
        List<BuildInvalidationReasons> getBuildInvalidationReasons();

        /**
         * Returns a list of project-level invalidation reasons. Only contains entries for projects that were invalidated.
         * This list is empty if the whole build is invalidated, i.e. when {@link #getBuildInvalidationReasons()} returns a non-empty list.
         * <p>
         * All invalidated project have entries in this list, however, not all invalidation reasons may be listed.
         * <p>
         * The first invalidation of the first project in the list is what Gradle shows to the user.
         * The rest of the invalidated projects follows, ordered by the {@link ProjectInvalidationReasons#getBuildPath()}, then {@link ProjectInvalidationReasons#getProjectPath()}.
         *
         * @return a list of invalidation projects with their invalidation reasons
         * @since 8.10
         */
        List<ProjectInvalidationReasons> getProjectInvalidationReasons();
    }

    /**
     * Contains invalidation for a single build.
     *
     * @since 8.10
     */
    public interface BuildInvalidationReasons {
        /**
         * Returns the path of the invalidated build in the build tree.
         *
         * @return the build path
         * @since 8.10
         */
        String getBuildPath();

        /**
         * Returns the list of the invalidation reasons for the given build. At least one invalidation is always present.
         *
         * @return the list of invalidation reasons
         * @since 8.10
         */
        List<FingerprintInvalidationReason> getInvalidationReasons();
    }

    /**
     * Contains data of a single invalidated project.
     *
     * @since 8.10
     */
    public interface ProjectInvalidationReasons {
        /**
         * Returns the build path of the invalidated project.
         *
         * @return the build path
         * @since 8.10
         */
        String getBuildPath();

        /**
         * Returns the project path of the invalidated project within the build.
         *
         * @return the project path
         * @since 8.10
         */
        String getProjectPath();

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

    /**
     * An overall result of the fingerprint check.
     *
     * @since 8.10
     */
    public enum CheckStatus {
        /**
         * Fingerprint file was not found. Most likely, there is no cached configuration for the given key. No invalidation reasons are reported.
         *
         * @since 8.10
         */
        NOT_FOUND,
        /**
         * The cached entry is valid and can be reused. No invalidation reasons are reported.
         *
         * @since 8.10
         */
        VALID,
        /**
         * Configuration of some projects is invalidated. Configuration phase is going to be re-executed but the data from unaffected project will be reused.
         * A list of invalidated projects and their invalidation reasons can be found in {@link Result#getProjectInvalidationReasons()}.
         *
         * @since 8.10
         */
        PARTIAL,
        /**
         * The cached entry is invalid. Configuration phase is going to be re-executed from scratch. A list of invalidation reasons can be found in {@link Result#getBuildInvalidationReasons()}.
         *
         * @since 8.10
         */
        INVALID
    }
}
