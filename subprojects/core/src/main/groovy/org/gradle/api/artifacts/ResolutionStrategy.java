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

import java.util.Set;

/**
 * Defines the strategies around forcing certain dependency versions or conflict resolutions.
 * Example:
 *
 * <pre autoTested=''>
 * configurations.all {
 *   //fail eagerly on conflict
 *   resolutionStrategy.conflictResolution = resolutionStrategy.strict()
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
     * @param conflictResolution to set
     */
    void setConflictResolution(ConflictResolution conflictResolution);

    /**
     * Configures forced versions in DSL friendly fashion
     *
     * @param forcedVersions gav notations
     */
    void force(String ... forcedVersions);

    /**
     * returns currently configured forced versions
     *
     * @return forced versions
     */
    Set<ForcedVersion> getForcedVersions();

    /**
     * use the latest of conflicting versions and move on
     */
    ConflictResolution latest();

    /**
     * fail eagerly on conflict
     */
    ConflictResolution strict();
}
