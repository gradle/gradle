/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.internal.artifacts.configurations.DefaultResolutionStrategy;

import java.util.Set;

/**
 * Defines the strategies around forcing certain dependency versions or conflict resolutions.
 * Example:
 *
 * <pre autoTested=''>
 * configurations.all {
 *   //fail eagerly on conflict
 *   resolutionStrategy.conflictResolution = resolutionStrategy.strict()
 *   // cache dynamic versions for 10 minutes
 *   resolutionStrategy.expireDynamicVersionsAfter 10, 'minutes'
 * }
 * </pre>
 */
public interface ResolutionStrategy {

    /**
     * gets current conflict resolution
     *
     * @return conflict resolution
     */
    ConflictResolution getConflictResolution();

    /**
     * configures conflict resolution
     *
     *
     * @param conflictResolution to set
     * @return this ResolutionStrategy instance
     */
    DefaultResolutionStrategy setConflictResolution(ConflictResolution conflictResolution);

    /**
     * Configures forced versions in DSL friendly fashion
     *
     *
     * @param forcedVersions gav notations
     * @return this ResolutionStrategy instance
     */
    DefaultResolutionStrategy force(String... forcedVersions);

    /**
     * Replaces existing forced versions with the passed ones.
     *
     * @param forcedVersions forced versions to set
     * @return this ResolutionStrategy instance
     */
    ResolutionStrategy setForce(Iterable<ForcedVersion> forcedVersions);

    /**
     * returns currently configured forced versions
     *
     * @return forced versions
     */
    Set<ForcedVersion> getForce();

    /**
     * use the latest of conflicting versions and move on
     */
    ConflictResolution latest();

    /**
     * fail eagerly on conflict
     */
    ConflictResolution strict();

    /**
     * Gradle keeps a cache of dynamic version => resolved version (ie 2.+ => 2.3). By default, these cached values are kept for 24 hours, after which the cached entry is expired
     * and the dynamic version is resolved again.
     * Use this method to provide a custom expiry time after which the cached value for any dynamic version will be expired.
     * Units are resolved using {@link java.util.concurrent.TimeUnit#valueOf(String)} on the upper-cased string value.
     * @param value The number of time units
     * @param units The units
     */
    void expireDynamicVersionsAfter(Integer value, String units);
}
