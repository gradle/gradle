/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Represents a constrained version. By default, when a dependency has a version number, it is assumed
 * that the version can be upgraded during conflict resolution (typically, version 1.15 can be upgraded to 1.16). However
 * in some cases we don't want this behavior. This class represents the base spec of module constraints.
 * @since 4.4
 */
@Incubating
public interface VersionConstraint {
    /**
     * The branch to select versions from. When not {@code null} selects only those versions that were built from the specified branch.
     *
     * @since 4.6
     */
    @Nullable
    String getBranch();

    /**
     * The preferred version of a module (which may be an exact version or a version range).
     *
     * The preferred version of a module can typically be upgraded during dependency resolution, unless further constraints are added.
     *
     * @return the preferred version, or empty string if no preferred version specified. Never null.
     */
    String getPreferredVersion();

    /**
     * The strictly required version of a module (which may be an exact version or a version range).
     *
     * The required version of a module is strictly enforced and cannot be upgraded or downgraded during dependency resolution.
     *
     * @return the strict version, or empty string if no required version specified. Never null.
     */
    String getStrictVersion();

    /**
     * Returns the list of versions that this module rejects  (which may be exact versions, or ranges, anything that fits into a version string).
     *
     * @return the list of rejected versions
     */
    List<String> getRejectedVersions();
}
